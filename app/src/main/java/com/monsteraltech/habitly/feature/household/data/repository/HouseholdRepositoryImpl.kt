package com.monsteraltech.habitly.feature.household.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.MemberProfile
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.household.domain.repository.InvalidInviteCodeException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

class HouseholdRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : HouseholdRepository {

    private val secureRandom = SecureRandom()

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val nickname = displayName.split(" ").firstOrNull().orEmpty().ifBlank { displayName }
                val userProfile = UserProfile(
                    id = userId,
                    displayName = displayName,
                    nickname = nickname,
                    activeHouseholdId = ""
                )
                userRef.set(userProfile).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<String> {
        return try {
            val newHouseholdId = UUID.randomUUID().toString()
            val inviteCode = generateUniqueInviteCode()
            val expiresAt = newInviteExpiry()

            firestore.collection("households").document(newHouseholdId).set(household).await()
            firestore.collection("users").document(userId)
                .update("activeHouseholdId", newHouseholdId).await()
            registerInviteCode(inviteCode, newHouseholdId, expiresAt)

            Result.success(newHouseholdId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun generateUniqueInviteCode(): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = randomCode()
            val exists = firestore.collection("invite_codes").document(candidate).get().await().exists()
            if (!exists) return candidate
        }
        return randomCode() + randomCode().take(2)
    }

    private fun randomCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    private fun newInviteExpiry(): Long = System.currentTimeMillis() + INVITE_CODE_TTL_MS

    private suspend fun registerInviteCode(code: String, householdId: String, expiresAt: Long) {
        firestore.collection("invite_codes").document(code)
            .set(
                mapOf(
                    "householdId" to householdId,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to expiresAt
                )
            )
            .await()
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
            val normalizedCode = inviteCode.uppercase().trim()

            val codeDoc = runCatching {
                firestore.collection("invite_codes").document(normalizedCode).get().await()
            }.getOrNull()

            val newHouseholdId = codeDoc?.getString("householdId")
            if (codeDoc?.exists() != true || newHouseholdId.isNullOrBlank()) {
                return Result.failure(InvalidInviteCodeException())
            }

            val userRef = firestore.collection("users").document(userId)
            val userSnapshot = userRef.get().await()
            val userProfile = userSnapshot.toObject(UserProfile::class.java)

            val oldHouseholdId = userProfile?.activeHouseholdId

            if (oldHouseholdId == newHouseholdId) {
                return Result.failure(Exception("Already a member of this household"))
            }

            val batch = firestore.batch()

            if (!oldHouseholdId.isNullOrBlank()) {
                val oldHouseholdRef = firestore.collection("households").document(oldHouseholdId)
                batch.update(
                    oldHouseholdRef,
                    "members", FieldValue.arrayRemove(userId),
                    memberProfilePath(userId), FieldValue.delete()
                )
            }

            val targetHouseholdRef = firestore.collection("households").document(newHouseholdId)
            batch.update(
                targetHouseholdRef,
                "members", FieldValue.arrayUnion(userId),
                memberProfilePath(userId), MemberProfile(
                    displayName = userProfile?.displayName.orEmpty(),
                    nickname = userProfile?.nickname.orEmpty()
                )
            )
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

    override suspend fun updateNickname(
        userId: String,
        householdId: String,
        newNickname: String
    ): Result<Unit> {
        return try {
            val nickname = newNickname.trim()
            firestore.collection("users").document(userId)
                .update("nickname", nickname)
                .await()

            if (householdId.isNotBlank()) {
                firestore.collection("households").document(householdId)
                    .update(FieldPath.of(MEMBER_PROFILES, userId, "nickname"), nickname)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncOwnMemberProfile(
        householdId: String,
        userId: String,
        displayName: String,
        nickname: String
    ): Result<Unit> {
        return try {
            firestore.collection("households").document(householdId)
                .update(
                    memberProfilePath(userId),
                    MemberProfile(displayName = displayName, nickname = nickname)
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun claimHouseholdOwnership(householdId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("households").document(householdId)
                .update("ownerId", userId)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit> {
        return try {
            val householdRef = firestore.collection("households").document(householdId)
            val household = householdRef.get().await().toObject(Household::class.java)
            val heir = successorIfOwnerLeaves(household, userId)

            if (household != null && household.members.size > 1) {
                runCatching { rotateInviteCode(householdId) }
            }

            val batch = firestore.batch()
            if (heir != null) {
                batch.update(
                    householdRef,
                    "members", FieldValue.arrayRemove(userId),
                    memberProfilePath(userId), FieldValue.delete(),
                    "ownerId", heir
                )
            } else {
                batch.update(
                    householdRef,
                    "members", FieldValue.arrayRemove(userId),
                    memberProfilePath(userId), FieldValue.delete()
                )
            }
            batch.update(
                firestore.collection("users").document(userId),
                "activeHouseholdId", ""
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun successorIfOwnerLeaves(household: Household?, leavingUserId: String): String? {
        if (household == null || !household.isOwner(leavingUserId)) return null
        return household.members.firstOrNull { it != leavingUserId }
    }

    override suspend fun removeMember(householdId: String, memberId: String): Result<Unit> {
        return try {
            firestore.collection("households").document(householdId)
                .update(
                    "members", FieldValue.arrayRemove(memberId),
                    memberProfilePath(memberId), FieldValue.delete()
                )
                .await()

            runCatching { rotateInviteCode(householdId) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun regenerateInviteCode(householdId: String): Result<Unit> {
        return try {
            rotateInviteCode(householdId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sustituye el código de la casa por uno nuevo e invalida el anterior. Se llama al
     * regenerarlo a mano y también cuando alguien sale o es expulsado: si no, el código que
     * esa persona conoce le seguiría abriendo la puerta.
     */
    private suspend fun rotateInviteCode(householdId: String) {
        val householdRef = firestore.collection("households").document(householdId)
        val household = householdRef.get().await().toObject(Household::class.java)
            ?: throw IllegalStateException("Casa no encontrada")

        val oldCode = household.inviteCode
        val newCode = generateUniqueInviteCode()

        // 1) Nuevo mapping, 2) actualizar la casa, 3) borrar el mapping antiguo.
        val expiresAt = newInviteExpiry()
        registerInviteCode(newCode, householdId, expiresAt)
        householdRef.update("inviteCode", newCode, "inviteCodeExpiresAt", expiresAt).await()
        if (oldCode.isNotBlank()) {
            firestore.collection("invite_codes").document(oldCode).delete().await()
        }
    }

    override suspend fun clearActiveHousehold(userId: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("activeHouseholdId", "")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteUserData(userId: String): Result<Unit> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            val profile = userRef.get().await().toObject(UserProfile::class.java)

            val householdId = profile?.activeHouseholdId
            if (!householdId.isNullOrBlank()) {
                val householdRef = firestore.collection("households").document(householdId)
                val household = householdRef.get().await().toObject(Household::class.java)
                val heir = successorIfOwnerLeaves(household, userId)

                if (heir != null) {
                    householdRef.update(
                        "members", FieldValue.arrayRemove(userId),
                        memberProfilePath(userId), FieldValue.delete(),
                        "ownerId", heir
                    ).await()
                } else {
                    householdRef.update(
                        "members", FieldValue.arrayRemove(userId),
                        memberProfilePath(userId), FieldValue.delete()
                    ).await()
                }
            }

            val routines = userRef.collection("routines").get().await()
            val refsToDelete = mutableListOf<DocumentReference>()
            for (routine in routines.documents) {
                val completions = routine.reference.collection("completions").get().await()
                completions.documents.forEach { refsToDelete.add(it.reference) }
                refsToDelete.add(routine.reference)
            }
            refsToDelete.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            userRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun memberProfilePath(userId: String): FieldPath =
        FieldPath.of(MEMBER_PROFILES, userId)

    companion object {
        private const val MEMBER_PROFILES = "memberProfiles"
        private const val INVITE_CODE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
        private const val MAX_CODE_ATTEMPTS = 5
        private const val MAX_BATCH_OPS = 450
    }
}
