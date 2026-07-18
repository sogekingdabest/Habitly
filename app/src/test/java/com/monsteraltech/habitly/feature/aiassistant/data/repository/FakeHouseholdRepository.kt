package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.household.domain.model.Household
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeHouseholdRepository : HouseholdRepository {

    var stubProfile: UserProfile? = null

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> = Result.success(Unit)

    override suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<Unit> = Result.success(Unit)

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = flowOf(stubProfile)

    override fun observeHousehold(householdId: String): Flow<Household?> = flowOf(null)

    override suspend fun joinHousehold(userId: String, inviteCode: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateHouseholdName(householdId: String, newName: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateNickname(userId: String, newNickname: String): Result<Unit> = Result.success(Unit)

    override suspend fun getMemberProfiles(memberIds: List<String>): Result<List<UserProfile>> = Result.success(emptyList())

    override suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit> = Result.success(Unit)

    override suspend fun removeMember(householdId: String, memberId: String): Result<Unit> = Result.success(Unit)

    override suspend fun regenerateInviteCode(householdId: String): Result<Unit> = Result.success(Unit)

    override suspend fun clearActiveHousehold(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteUserData(userId: String): Result<Unit> = Result.success(Unit)

    fun reset() {
        stubProfile = null
    }
}
