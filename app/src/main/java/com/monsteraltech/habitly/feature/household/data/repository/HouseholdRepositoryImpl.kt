package com.monsteraltech.habitly.feature.household.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class HouseholdRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : HouseholdRepository {

    override suspend fun initializeUserAndHousehold(userId: String, displayName: String): Result<Unit> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val batch = firestore.batch()
                
                // Generar nueva casa
                val newHouseholdId = UUID.randomUUID().toString()
                val inviteCode = UUID.randomUUID().toString().substring(0, 6).uppercase()
                
                val household = Household(
                    id = newHouseholdId,
                    name = "Casa de $displayName",
                    inviteCode = inviteCode,
                    members = listOf(userId),
                    customStores = emptyList()
                )
                
                val householdRef = firestore.collection("households").document(newHouseholdId)
                batch.set(householdRef, household)
                
                // Crear usuario vinculado a esta casa
                val nickname = displayName.split(" ").first()
                val userProfile = UserProfile(
                    id = userId,
                    displayName = displayName,
                    nickname = nickname,
                    activeHouseholdId = newHouseholdId
                )
                batch.set(userRef, userProfile)
                
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { listener.remove() }
    }

    override fun observeHousehold(householdId: String): Flow<Household?> = callbackFlow {
        val listener = firestore.collection("households").document(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val household = snapshot?.toObject(Household::class.java)
                trySend(household)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun joinHousehold(userId: String, inviteCode: String): Result<Unit> {
        return try {
            val uppercaseCode = inviteCode.uppercase().trim()
            val querySnapshot = firestore.collection("households")
                .whereEqualTo("inviteCode", uppercaseCode)
                .get()
                .await()
                
            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Código de invitación no válido"))
            }
            
            val targetHouseholdDoc = querySnapshot.documents.first()
            val newHouseholdId = targetHouseholdDoc.id
            
            val batch = firestore.batch()
            
            val userRef = firestore.collection("users").document(userId)
            val userSnapshot = userRef.get().await()
            val userProfile = userSnapshot.toObject(UserProfile::class.java)
            
            val oldHouseholdId = userProfile?.activeHouseholdId
            
            if (oldHouseholdId == newHouseholdId) {
                return Result.failure(Exception("Ya perteneces a esta casa"))
            }
            
            if (oldHouseholdId != null) {
                val oldHouseholdRef = firestore.collection("households").document(oldHouseholdId)
                batch.update(oldHouseholdRef, "members", FieldValue.arrayRemove(userId))
            }
            
            batch.update(targetHouseholdDoc.reference, "members", FieldValue.arrayUnion(userId))
            batch.update(userRef, "activeHouseholdId", newHouseholdId)
            
            batch.commit().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateHouseholdName(householdId: String, newName: String): Result<Unit> {
        return try {
            firestore.collection("households").document(householdId)
                .update("name", newName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateNickname(userId: String, newNickname: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("nickname", newNickname.trim())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMemberProfiles(memberIds: List<String>): Result<List<UserProfile>> {
        return try {
            if (memberIds.isEmpty()) return Result.success(emptyList())
            
            val profiles = memberIds.mapNotNull { memberId ->
                try {
                    val snapshot = firestore.collection("users").document(memberId).get().await()
                    snapshot.toObject(UserProfile::class.java)?.copy(id = memberId)
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
