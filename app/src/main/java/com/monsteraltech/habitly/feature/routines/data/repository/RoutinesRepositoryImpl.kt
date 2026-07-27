package com.monsteraltech.habitly.feature.routines.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.StreakCalculator
import com.monsteraltech.habitly.feature.widget.domain.WidgetRefresher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RoutinesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val widgetRefresher: WidgetRefresher
) : RoutinesRepository {

    override fun observePersonalRoutines(userId: String): Flow<List<Routine>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            return@callbackFlow
        }

        val collection = firestore.collection("users").document(userId).collection("routines")
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val routines = snapshot.documents.mapNotNull { it.toObject(Routine::class.java)?.copy(id = it.id) }
                trySend(routines)
            }
        }

        awaitClose { listener.remove() }
    }

    override fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>> = callbackFlow {
        if (householdId.isBlank()) {
            trySend(emptyList())
            return@callbackFlow
        }

        val collection = firestore.collection("households").document(householdId).collection("routines")
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val routines = snapshot.documents.mapNotNull { it.toObject(Routine::class.java)?.copy(id = it.id) }
                trySend(routines)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit> {
        return try {
            val collection = collectionFor(routine.type, userId, householdId)
            collection.document(routine.id).set(routine).await()
            widgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType
    ): Result<Routine?> {
        return try {
            val document = documentFor(type, userId, householdId, routineId).get().await()
            Result.success(document.toObject(Routine::class.java)?.copy(id = document.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRoutineCompletion(
        userId: String,
        householdId: String,
        routine: Routine,
        completedAt: Long?,
        completedBy: String?
    ): Result<Unit> {
        return try {
            val routineRef = documentFor(routine.type, userId, householdId, routine.id)
            val completionsRef = routineRef.collection("completions")
            val zone = ZoneId.systemDefault()

            if (completedAt != null) {
                val dateId = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate().toString()
                completionsRef.document(dateId).set(
                    mapOf(
                        "date" to dateId,
                        "userId" to (completedBy ?: userId),
                        "completedAt" to completedAt
                    )
                ).await()
            } else {
                val today = LocalDate.now(zone).toString()
                completionsRef.document(today).delete().await()
            }

            val snapshot = completionsRef
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(MAX_COMPLETIONS_SCANNED)
                .get()
                .await()
            val dates = snapshot.toLocalDates()

            val streak = StreakCalculator.forRoutine(
                routine = routine.copy(lastCompletedAt = completedAt, lastCompletedBy = completedBy),
                completedDates = dates,
                today = LocalDate.now(zone)
            )

            // 3. Denormalizamos en el documento de la rutina (para pintarla sin listeners extra).
            routineRef.update(
                mapOf(
                    "lastCompletedAt" to completedAt,
                    "lastCompletedBy" to completedBy,
                    "currentStreak" to streak.current,
                    "bestStreak" to streak.best,
                    "streakGraceUsed" to streak.graceUsed
                )
            ).await()

            widgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCompletions(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        from: LocalDate,
        to: LocalDate
    ): Result<List<RoutineCompletion>> {
        return try {
            val snapshot = documentFor(type, userId, householdId, routineId)
                .collection("completions")
                .whereGreaterThanOrEqualTo("date", from.toString())
                .whereLessThanOrEqualTo("date", to.toString())
                .limit(MAX_COMPLETIONS_SCANNED)
                .get()
                .await()

            val completions = snapshot.documents.mapNotNull { doc ->
                val value = doc.getString("date") ?: doc.id
                val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return@mapNotNull null
                RoutineCompletion(date = date, userId = doc.getString("userId").orEmpty())
            }
            Result.success(completions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRoutineAssignment(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        assignedTo: String?
    ): Result<Unit> {
        return try {
            documentFor(type, userId, householdId, routineId)
                .update("assignedTo", assignedTo)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType
    ): Result<Unit> {
        return try {
            documentFor(type, userId, householdId, routineId).delete().await()
            widgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRoutine(
        userId: String,
        householdId: String,
        routine: Routine
    ): Result<Unit> {
        return try {
            documentFor(routine.type, userId, householdId, routine.id)
                .update(
                    mapOf(
                        "title" to routine.title.trim(),
                        "description" to routine.description.trim(),
                        "frequency" to routine.frequency.name,
                        "scheduledDays" to routine.scheduledDays,
                        "reminderTime" to routine.reminderTime,
                        "intervalDays" to routine.intervalDays,
                        "pausedUntil" to routine.pausedUntil,
                        "assignedTo" to routine.assignedTo,
                        "rotationEnabled" to routine.rotationEnabled
                    )
                ).await()
            widgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderRoutines(
        userId: String,
        householdId: String,
        type: RoutineType,
        orderedIds: List<String>
    ): Result<Unit> {
        return try {
            val collection = collectionFor(type, userId, householdId)
            val batch = firestore.batch()
            orderedIds.forEachIndexed { index, routineId ->
                batch.update(collection.document(routineId), "order", index)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun com.google.firebase.firestore.QuerySnapshot.toLocalDates(): List<LocalDate> =
        documents.mapNotNull { doc ->
            val value = doc.getString("date") ?: doc.id
            runCatching { LocalDate.parse(value) }.getOrNull()
        }

    private fun collectionFor(type: RoutineType, userId: String, householdId: String): CollectionReference =
        if (type == RoutineType.PERSONAL) {
            firestore.collection("users").document(userId).collection("routines")
        } else {
            firestore.collection("households").document(householdId).collection("routines")
        }

    private fun documentFor(type: RoutineType, userId: String, householdId: String, routineId: String) =
        collectionFor(type, userId, householdId).document(routineId)

    private companion object {
        const val MAX_COMPLETIONS_SCANNED = 370L
    }
}
