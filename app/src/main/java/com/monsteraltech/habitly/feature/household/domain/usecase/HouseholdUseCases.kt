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
    suspend operator fun invoke(userId: String, displayName: String, householdName: String): Result<Unit> {
        if (householdName.isBlank()) return Result.failure(Exception("El nombre no puede estar vacío"))
        return repository.createHousehold(userId, displayName, householdName.trim())
    }
}

class LeaveHouseholdUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(userId: String, householdId: String): Result<Unit> {
        if (householdId.isBlank()) return Result.failure(Exception("No hay casa activa"))
        return repository.leaveHousehold(userId, householdId)
    }
}

class RemoveMemberUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(householdId: String, memberId: String): Result<Unit> {
        if (householdId.isBlank() || memberId.isBlank()) return Result.failure(Exception("Datos inválidos"))
        return repository.removeMember(householdId, memberId)
    }
}

class RegenerateInviteCodeUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    suspend operator fun invoke(householdId: String): Result<Unit> {
        if (householdId.isBlank()) return Result.failure(Exception("No hay casa activa"))
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

/**
 * Borra la cuenta del usuario: primero sus datos de Firestore (mientras sigue
 * autenticado) y después la cuenta de autenticación. Si Firebase exige un login
 * reciente, la cuenta de Auth no se borra y se propaga el error para que la UI
 * pida volver a iniciar sesión.
 */
class DeleteAccountUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val user = authRepository.getCurrentUser()
            ?: return Result.failure(Exception("No hay usuario activo"))

        val dataResult = householdRepository.deleteUserData(user.uid)
        if (dataResult.isFailure) return dataResult

        return authRepository.deleteAccount()
    }
}
