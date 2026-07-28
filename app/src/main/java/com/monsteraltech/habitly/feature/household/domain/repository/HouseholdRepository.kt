package com.monsteraltech.habitly.feature.household.domain.repository

import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * The code is unusable: it does not exist, it has expired, or it points at a household that is
 * gone. The three cases collapse into one deliberately, so probing does not reveal which codes
 * exist.
 */
class InvalidInviteCodeException :
    Exception("El código de invitación no es válido o ha caducado")

interface HouseholdRepository {
    /**
     * Creates the user profile if missing, with **no** household attached (empty
     * activeHouseholdId). Onboarding then decides whether to create one or join one.
     */
    suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit>

    /** Creates the household, marks it as the user's active one and returns **its id**. */
    suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<String>

    fun observeUserProfile(userId: String): Flow<UserProfile?>

    fun observeHousehold(householdId: String): Flow<Household?>

    suspend fun joinHousehold(userId: String, inviteCode: String): Result<Unit>

    suspend fun updateHouseholdName(householdId: String, newName: String): Result<Unit>

    /**
     * Updates the nickname and propagates it to the public copy living in the household document.
     * A blank [householdId] (user without a household yet) skips the propagation.
     */
    suspend fun updateNickname(userId: String, householdId: String, newNickname: String): Result<Unit>

    /**
     * Writes [userId]'s entry into the household's `memberProfiles`. Idempotent and meant to be
     * called on every launch: it backfills households created before the field existed, with no
     * migration script.
     */
    suspend fun syncOwnMemberProfile(
        householdId: String,
        userId: String,
        displayName: String,
        nickname: String
    ): Result<Unit>

    /**
     * Sets [userId] as the household owner. Only has an effect on households created before the
     * field existed: the rules allow filling it in only when it is empty and the claimant is the
     * first member, i.e. whoever created it.
     */
    suspend fun claimHouseholdOwnership(householdId: String, userId: String): Result<Unit>

    /** Removes the user from members and empties their activeHouseholdId, back to onboarding. */
    suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit>

    suspend fun removeMember(householdId: String, memberId: String): Result<Unit>

    /** Issues a new invite code and invalidates the previous one. */
    suspend fun regenerateInviteCode(householdId: String): Result<Unit>

    /**
     * Resets the user's active household to empty — self-healing for someone who was removed but
     * whose profile still points at a household they no longer belong to.
     */
    suspend fun clearActiveHousehold(userId: String): Result<Unit>

    /**
     * Wipes the user's Firestore data: removes them from their household, deletes their personal
     * routines and their profile document. Called before deleting the auth account.
     */
    suspend fun deleteUserData(userId: String): Result<Unit>
}
