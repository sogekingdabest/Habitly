package com.monsteraltech.habitly.feature.routines.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.routines.data.cache.RoutinesCacheManager
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RoutinesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val cacheManager: RoutinesCacheManager
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
            val collection = if (routine.type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines")
            } else {
                firestore.collection("households").document(householdId).collection("routines")
            }
            collection.document(routine.id).set(routine).await()
            if (routine.type == RoutineType.PERSONAL) {
                val current = cacheManager.observePersonalRoutines(userId).first()
                cacheManager.cachePersonalRoutines(userId, current + routine)
            } else {
                val current = cacheManager.observeHouseholdRoutines(householdId).first()
                cacheManager.cacheHouseholdRoutines(householdId, current + routine)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (routine.type == RoutineType.PERSONAL) {
                val current = cacheManager.observePersonalRoutines(userId).first()
                cacheManager.cachePersonalRoutines(userId, current + routine)
            } else {
                val current = cacheManager.observeHouseholdRoutines(householdId).first()
                cacheManager.cacheHouseholdRoutines(householdId, current + routine)
            }
            Result.success(Unit)
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
            val document = if (type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines").document(routineId)
            } else {
                firestore.collection("households").document(householdId).collection("routines").document(routineId)
            }
            
            document.update(
                mapOf(
                    "lastCompletedAt" to completedAt,
                    "lastCompletedBy" to completedBy
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    override suspend fun deleteRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType
    ): Result<Unit> {
        return try {
            val document = if (type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines").document(routineId)
            } else {
                firestore.collection("households").document(householdId).collection("routines").document(routineId)
            }
            document.delete().await()
            cacheManager.removeRoutine(userId, householdId, routineId, type)
            Result.success(Unit)
        } catch (e: Exception) {
            cacheManager.removeRoutine(userId, householdId, routineId, type)
            Result.success(Unit)
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
            val document = if (type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines").document(routineId)
            } else {
                firestore.collection("households").document(householdId).collection("routines").document(routineId)
            }
            document.update(
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
            Result.success(Unit)
        }
    }

    override suspend fun reorderRoutines(
        userId: String,
        householdId: String,
        type: RoutineType,
        orderedIds: List<String>
    ): Result<Unit> {
        return try {
            val collection = if (type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines")
            } else {
                firestore.collection("households").document(householdId).collection("routines")
            }
            val batch = firestore.batch()
            orderedIds.forEachIndexed { index, routineId ->
                val document = collection.document(routineId)
                batch.update(document, "order", index)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}
