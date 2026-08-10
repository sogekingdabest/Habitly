package com.monsteraltech.habitly.feature.routines.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineComment
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
    // The widget lists today's pending routines: any write that changes them has to repaint it, or
    // it keeps showing something that is no longer true.
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

            // 1. Record/remove the day's completion in the history subcollection.
            //    Doc id = ISO date (yyyy-MM-dd) => idempotent, one completion per day.
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

            // 2. Recompute the streak from the history (capped to the last year).
            val snapshot = completionsRef
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(MAX_COMPLETIONS_SCANNED)
                .get()
                .await()
            val dates = snapshot.toLocalDates()

            // The streak depends on the frequency, so it is computed from the already-updated routine.
            val streak = StreakCalculator.forRoutine(
                routine = routine.copy(lastCompletedAt = completedAt, lastCompletedBy = completedBy),
                completedDates = dates,
                today = LocalDate.now(zone)
            )

            // 3. Denormalise onto the routine document, so it paints without extra listeners.
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
            // The ids/fields are ISO dates (yyyy-MM-dd), so lexicographic order matches chronological
            // order and a range can be filtered as text.
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
            val routineRef = documentFor(type, userId, householdId, routineId)
            // Firestore does not cascade document deletion to nested collections. Delete the
            // bounded batches first and the parent last, so a failure never leaves an invisible
            // routine with completion or comment documents still accruing storage.
            deleteCollection(routineRef.collection("completions"))
            deleteCollection(routineRef.collection("comments"))
            routineRef.delete().await()
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
                        "icon" to routine.icon,
                        "notificationLevel" to routine.notificationLevel.name,
                        "frequency" to routine.frequency.name,
                        "scheduledDays" to routine.scheduledDays,
                        "reminderTime" to routine.reminderTime,
                        "intervalDays" to routine.intervalDays,
                        "pausedUntil" to routine.pausedUntil,
                        "startDate" to routine.startDate,
                        "endDate" to routine.endDate,
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

    // ---------- Comments ----------

    override fun observeComments(householdId: String, routineId: String): Flow<List<RoutineComment>> =
        callbackFlow {
            if (householdId.isBlank() || routineId.isBlank()) {
                trySend(emptyList())
                return@callbackFlow
            }

            val listener = commentsRef(householdId, routineId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(MAX_COMMENTS_SHOWN)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val comments = snapshot.documents.mapNotNull {
                            it.toObject(RoutineComment::class.java)?.copy(id = it.id)
                        }
                        trySend(comments)
                    }
                }

            awaitClose { listener.remove() }
        }

    override suspend fun addComment(
        householdId: String,
        routineId: String,
        comment: RoutineComment
    ): Result<Unit> {
        return try {
            val routineRef = documentFor(RoutineType.HOUSEHOLD, "", householdId, routineId)
            // One batch so the comment and the counter can never disagree.
            firestore.batch().apply {
                set(commentsRef(householdId, routineId).document(comment.id), comment)
                update(routineRef, "commentCount", FieldValue.increment(1))
            }.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteComment(
        householdId: String,
        routineId: String,
        commentId: String
    ): Result<Unit> {
        return try {
            val routineRef = documentFor(RoutineType.HOUSEHOLD, "", householdId, routineId)
            firestore.batch().apply {
                delete(commentsRef(householdId, routineId).document(commentId))
                update(routineRef, "commentCount", FieldValue.increment(-1))
            }.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Comments only exist on household routines, so the path is always the household one. No new
     * Firestore rules were needed: the recursive wildcard over a household's subcollections already
     * limits this to its members.
     */
    private fun commentsRef(householdId: String, routineId: String): CollectionReference =
        firestore.collection("households").document(householdId)
            .collection("routines").document(routineId)
            .collection("comments")

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

    private suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            val documents = collection.limit(DELETE_BATCH_SIZE.toLong()).get().await().documents
            if (documents.isEmpty()) return

            firestore.batch().apply {
                documents.forEach { delete(it.reference) }
            }.commit().await()
        }
    }

    private companion object {
        // Caps the history reads: ~1 year is more than enough for the streak.
        const val MAX_COMPLETIONS_SCANNED = 370L

        // Caps the live listener: a routine's conversation is a handful of lines, not a chat log.
        const val MAX_COMMENTS_SHOWN = 100L

        // Leaves room below Firestore's 500-operation batch limit if more operations are added.
        const val DELETE_BATCH_SIZE = 400
    }
}
