package com.monsteraltech.habitly.feature.household.domain.usecase

import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EnsureUserProfileUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, displayName: String): Result<Unit> {
        return repository.ensureUserProfile(userId, displayName)
    }
}

class CreateHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, displayName: String, householdName: String): Result<String> {
        if (householdName.isBlank()) return Result.failure(Exception("Household name cannot be empty"))
        return repository.createHousehold(userId, displayName, householdName.trim())
    }
}

class LeaveHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, householdId: String): Result<Unit> {
        if (householdId.isBlank()) return Result.failure(Exception("No active household"))
        return repository.leaveHousehold(userId, householdId)
    }
}

class RemoveMemberUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(
        household: Household,
        requesterId: String,
        memberId: String
    ): Result<Unit> {
        if (household.id.isBlank() || memberId.isBlank()) {
            return Result.failure(Exception("Invalid data"))
        }
        if (!household.isOwner(requesterId)) {
            return Result.failure(Exception("Only the household owner can remove members"))
        }
        if (memberId == requesterId) {
            return Result.failure(Exception("To leave the household, use 'Leave Household'"))
        }
        return repository.removeMember(household.id, memberId)
    }
}

class BackfillHouseholdOwnerUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(householdId: String, household: Household, userId: String): Result<Unit> {
        if (householdId.isBlank() || userId.isBlank()) return Result.success(Unit)
        if (household.ownerId.isNotBlank()) return Result.success(Unit)
        if (household.members.firstOrNull() != userId) return Result.success(Unit)
        return repository.claimHouseholdOwnership(householdId, userId)
    }
}

class RegenerateInviteCodeUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(householdId: String): Result<Unit> {
        if (householdId.isBlank()) return Result.failure(Exception("No active household"))
        return repository.regenerateInviteCode(householdId)
    }
}

class ClearActiveHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        return repository.clearActiveHousehold(userId)
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
        if (inviteCode.isBlank()) return Result.failure(Exception("Invite code cannot be empty"))
        return repository.joinHousehold(userId, inviteCode)
    }
}

class EditHouseholdNameUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(householdId: String, newName: String): Result<Unit> {
        if (newName.isBlank()) return Result.failure(Exception("Name cannot be empty"))
        return repository.updateHouseholdName(householdId, newName.trim())
    }
}

class UpdateNicknameUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, householdId: String, newNickname: String): Result<Unit> {
        if (newNickname.isBlank()) return Result.failure(Exception("Nickname cannot be empty"))
        return repository.updateNickname(userId, householdId, newNickname.trim())
    }
}

class GetMemberProfilesUseCase @Inject constructor() {
    operator fun invoke(household: Household): List<UserProfile> {
        return household.members.map { memberId ->
            val profile = household.memberProfiles[memberId]
            UserProfile(
                id = memberId,
                displayName = profile?.displayName.orEmpty(),
                nickname = profile?.nickname.orEmpty(),
                activeHouseholdId = household.id
            )
        }
    }
}

class SyncOwnMemberProfileUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(
        householdId: String,
        household: Household,
        userId: String,
        profile: UserProfile
    ): Result<Unit> {
        if (householdId.isBlank() || userId.isBlank()) return Result.success(Unit)

        val current = household.memberProfiles[userId]
        if (current != null &&
            current.displayName == profile.displayName &&
            current.nickname == profile.nickname
        ) {
            return Result.success(Unit)
        }

        return repository.syncOwnMemberProfile(
            householdId = householdId,
            userId = userId,
            displayName = profile.displayName,
            nickname = profile.nickname
        )
    }
}

class DeleteAccountUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val user = authRepository.getCurrentUser()
            ?: return Result.failure(Exception("No active user"))

        val dataResult = householdRepository.deleteUserData(user.uid)
        if (dataResult.isFailure) return dataResult

        return authRepository.deleteAccount()
    }
}
