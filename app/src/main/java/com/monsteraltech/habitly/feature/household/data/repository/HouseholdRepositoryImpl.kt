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

            // Same default nickname that ensureUserProfile computes.
            val nickname = displayName.split(" ").firstOrNull().orEmpty().ifBlank { displayName }

            val household = Household(
                id = newHouseholdId,
                name = householdName.trim().ifBlank { "Casa de $displayName" },
                inviteCode = inviteCode,
                inviteCodeExpiresAt = expiresAt,
                ownerId = userId,
                members = listOf(userId),
                customStores = emptyList(),
                memberProfiles = mapOf(
                    userId to MemberProfile(displayName = displayName, nickname = nickname)
                )
            )

            // 1) Create the household (the user is already listed as a member).
            firestore.collection("households").document(newHouseholdId).set(household).await()
            // 2) Mark it as the user's active household.
            firestore.collection("users").document(userId)
                .update("activeHouseholdId", newHouseholdId).await()
            // 3) Register the code mapping — requires membership already, hence last.
            registerInviteCode(inviteCode, newHouseholdId, expiresAt)

            // The id goes back to the caller: onboarding needs it to create the template routines
            // there without re-reading the profile it just wrote.
            Result.success(newHouseholdId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a readable code (no ambiguous characters) with SecureRandom and guarantees it does
     * not already exist in the invite_codes collection.
     */
    private suspend fun generateUniqueInviteCode(): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = randomCode()
            val exists = firestore.collection("invite_codes").document(candidate).get().await().exists()
            if (!exists) return candidate
        }
        // Improbable fallback: add extra entropy.
        return randomCode() + randomCode().take(2)
    }

    private fun randomCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    /** When a code issued right now would expire (epoch ms). */
    private fun newInviteExpiry(): Long = System.currentTimeMillis() + INVITE_CODE_TTL_MS

    private suspend fun registerInviteCode(code: String, householdId: String, expiresAt: Long) {
        firestore.collection("invite_codes").document(code)
            .set(
                mapOf(
                    "householdId" to householdId,
                    "createdAt" to System.currentTimeMillis(),
                    // A code without expiry is a permanent key: anyone who leaves the household,
                    // or is removed from it, could walk back in months later with the code they
                    // memorised. The read rule validates this field.
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

            // The code is resolved with a single get against invite_codes — no open query over
            // households, which non-members can no longer read.
            //
            // The read rule requires the code not to be expired, so an expired one arrives as
            // PERMISSION_DENIED and a non-existent one as an empty document. Both end up as the
            // same InvalidInviteCodeException: neither yields a householdId, and the user-facing
            // message is identical either way.
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
                return Result.failure(Exception("Ya perteneces a esta casa"))
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

            // members and memberProfiles are written **together**, in one operation: the self-join
            // rule requires the entry added to memberProfiles to be the user's own, and split
            // across two writes the first one would be rejected.
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

            // The public copy of the name lives in the household; without this the other members
            // would keep seeing the old nickname until the next sync.
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
            // Plain write: deciding **whether** a write is needed belongs to
            // SyncOwnMemberProfileUseCase, which already has the household loaded and thereby
            // saves a Firestore read on every launch.
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

            // Done before leaving, while still a member — creating the new code mapping requires
            // membership. This invalidates the code the leaver memorised. If it fails, leaving is
            // still the priority.
            if (household != null && household.members.size > 1) {
                runCatching { rotateInviteCode(householdId) }
            }

            val batch = firestore.batch()
            if (heir != null) {
                // If the owner leaves, nobody can remove members or delete the household. Handing
                // ownership to the next member avoids that limbo.
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

    /**
     * Who inherits ownership when [leavingUserId] leaves. Null if they were not the owner, or if
     * there is nobody left to hand it to (the household ends up empty, so it does not matter).
     */
    private fun successorIfOwnerLeaves(household: Household?, leavingUserId: String): String? {
        if (household == null || !household.isOwner(leavingUserId)) return null
        return household.members.firstOrNull { it != leavingUserId }
    }

    override suspend fun removeMember(householdId: String, memberId: String): Result<Unit> {
        return try {
            // Only the household is touched (members and their public profile). The removed member
            // self-heals via clearActiveHousehold once it notices it no longer belongs.
            firestore.collection("households").document(householdId)
                .update(
                    "members", FieldValue.arrayRemove(memberId),
                    memberProfilePath(memberId), FieldValue.delete()
                )
                .await()

            // Removing someone without rotating the code removes nobody: they would rejoin with
            // the same code. If the rotation fails the removal is already done, which is what was
            // asked for; the code can be regenerated by hand.
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
     * Replaces the household code with a new one and invalidates the previous one. Called on a
     * manual regeneration and whenever someone leaves or is removed — otherwise the code that
     * person knows would keep the door open for them.
     */
    private suspend fun rotateInviteCode(householdId: String) {
        val householdRef = firestore.collection("households").document(householdId)
        val household = householdRef.get().await().toObject(Household::class.java)
            ?: throw IllegalStateException("Casa no encontrada")

        val oldCode = household.inviteCode
        val newCode = generateUniqueInviteCode()

        // 1) New mapping, 2) update the household, 3) delete the old mapping.
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

            // 1) Remove the user from their household, public profile copy included: leaving it
            //    behind would keep a deleted account's name visible to everyone else. And if they
            //    owned the household, hand it over before disappearing.
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

            // 2) Delete their personal routines. Firestore does **not** cascade-delete
            //    subcollections: each routine's completions must be collected too, or they would
            //    survive as orphans — personal data retained after account deletion.
            val routines = userRef.collection("routines").get().await()
            val refsToDelete = mutableListOf<DocumentReference>()
            for (routine in routines.documents) {
                val completions = routine.reference.collection("completions").get().await()
                completions.documents.forEach { refsToDelete.add(it.reference) }
                refsToDelete.add(routine.reference)
            }
            // A batch takes at most 500 operations; chunked with margin.
            refsToDelete.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            // 3) Delete the profile document.
            userRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Path to a member's public profile. Built with [FieldPath] rather than the string
     * "memberProfiles.$uid" because in a string the dot separates levels: a uid containing a
     * special character would split the path and write to the wrong place.
     */
    private fun memberProfilePath(userId: String): FieldPath =
        FieldPath.of(MEMBER_PROFILES, userId)

    companion object {
        private const val MEMBER_PROFILES = "memberProfiles"

        /** Lifetime of an invite code. Can be regenerated by hand at any time. */
        private const val INVITE_CODE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        // Alphabet without ambiguous characters (no I, O, 0, 1).
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
        private const val MAX_CODE_ATTEMPTS = 5

        // Firestore's real limit is 500 operations per batch; kept with margin.
        private const val MAX_BATCH_OPS = 450
    }
}
