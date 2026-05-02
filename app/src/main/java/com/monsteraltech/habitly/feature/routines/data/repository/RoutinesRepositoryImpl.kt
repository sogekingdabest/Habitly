package com.monsteraltech.habitly.feature.routines.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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
            val collection = if (routine.type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines")
            } else {
                firestore.collection("households").document(householdId).collection("routines")
            }
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
            val document = if (type == RoutineType.PERSONAL) {
                firestore.collection("users").document(userId).collection("routines").document(routineId)
            } else {
                firestore.collection("households").document(householdId).collection("routines").document(routineId)
            }
            document.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
