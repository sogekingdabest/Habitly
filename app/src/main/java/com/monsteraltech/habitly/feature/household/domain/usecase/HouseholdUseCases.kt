package com.monsteraltech.habitly.feature.household.domain.usecase

import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InitializeUserAndHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, displayName: String): Result<Unit> {
        return repository.initializeUserAndHousehold(userId, displayName)
    }
}

class ObserveUserProfileUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    operator fun invoke(userId: String): Flow<UserProfile?> {
        return repository.observeUserProfile(userId)
    }
}

class ObserveHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    operator fun invoke(householdId: String): Flow<Household?> {
        return repository.observeHousehold(householdId)
    }
}

class JoinHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, inviteCode: String): Result<Unit> {
        if (inviteCode.isBlank()) return Result.failure(Exception("El código no puede estar vacío"))
        return repository.joinHousehold(userId, inviteCode)
    }
}

class EditHouseholdNameUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(householdId: String, newName: String): Result<Unit> {
        if (newName.isBlank()) return Result.failure(Exception("El nombre no puede estar vacío"))
        return repository.updateHouseholdName(householdId, newName.trim())
    }
}

class UpdateNicknameUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, newNickname: String): Result<Unit> {
        if (newNickname.isBlank()) return Result.failure(Exception("El nickname no puede estar vacío"))
        return repository.updateNickname(userId, newNickname.trim())
    }
}

class GetMemberProfilesUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(memberIds: List<String>): Result<List<UserProfile>> {
        return repository.getMemberProfiles(memberIds)
    }
}
