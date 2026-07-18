package com.monsteraltech.habitly.feature.routines.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.StreakCalculator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RoutinesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRoutineCompletion(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        completedAt: Long?,
        completedBy: String?
    ): Result<Unit> {
        return try {
            val routineRef = documentFor(type, userId, householdId, routineId)
            val completionsRef = routineRef.collection("completions")
            val zone = ZoneId.systemDefault()

            // 1. Registramos/eliminamos el completado del día en la subcolección de historial.
            //    Doc id = fecha ISO (yyyy-MM-dd) => idempotente, un completado por día.
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

            // 2. Recalculamos la racha desde el historial (acotado al último año).
            val snapshot = completionsRef
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(MAX_COMPLETIONS_SCANNED)
                .get()
                .await()
            val dates = snapshot.documents.mapNotNull { doc ->
                val value = doc.getString("date") ?: doc.id
                runCatching { LocalDate.parse(value) }.getOrNull()
            }
            val streak = StreakCalculator.calculate(dates)

            // 3. Denormalizamos en el documento de la rutina (para pintarla sin listeners extra).
            routineRef.update(
                mapOf(
                    "lastCompletedAt" to completedAt,
                    "lastCompletedBy" to completedBy,
                    "currentStreak" to streak.current,
                    "bestStreak" to streak.best
                )
            ).await()

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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        title: String,
        description: String,
        frequency: RoutineFrequency,
        scheduledDays: List<Int>,
        reminderTime: Int?
    ): Result<Unit> {
        return try {
            documentFor(type, userId, householdId, routineId)
                .update(
                    mapOf(
                        "title" to title.trim(),
                        "description" to description.trim(),
                        "frequency" to frequency.name,
                        "scheduledDays" to scheduledDays,
                        "reminderTime" to reminderTime
                    )
                ).await()
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

    private fun collectionFor(type: RoutineType, userId: String, householdId: String) =
        if (type == RoutineType.PERSONAL) {
            firestore.collection("users").document(userId).collection("routines")
        } else {
            firestore.collection("households").document(householdId).collection("routines")
        }

    private fun documentFor(type: RoutineType, userId: String, householdId: String, routineId: String) =
        collectionFor(type, userId, householdId).document(routineId)

    private companion object {
        // Acota las lecturas del historial: ~1 año es más que suficiente para la racha.
        const val MAX_COMPLETIONS_SCANNED = 370L
    }
}
