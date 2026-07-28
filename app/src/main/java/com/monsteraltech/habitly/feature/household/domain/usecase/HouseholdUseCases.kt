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
    /** @return the id of the created household. */
    suspend operator fun invoke(userId: String, displayName: String, householdName: String): Result<String> {
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
    suspend operator fun invoke(
        household: Household,
        requesterId: String,
        memberId: String
    ): Result<Unit> {
        if (household.id.isBlank() || memberId.isBlank()) {
            return Result.failure(Exception("Datos inválidos"))
        }
        // The Firestore rule already blocks this, but checking here avoids a write doomed to fail
        // and allows a decent message instead of PERMISSION_DENIED.
        if (!household.isOwner(requesterId)) {
            return Result.failure(Exception("Solo quien creó la casa puede expulsar miembros"))
        }
        if (memberId == requesterId) {
            return Result.failure(Exception("Para salir de la casa, usa 'Salir de la casa'"))
        }
        return repository.removeMember(household.id, memberId)
    }
}

/**
 * Backfills [Household.ownerId] in households created before the field existed. Only writes when
 * it is empty and the caller is the first member (whoever created it); the rules enforce the same
 * thing server-side.
 */
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
    suspend operator fun invoke(userId: String, householdId: String, newNickname: String): Result<Unit> {
        if (newNickname.isBlank()) return Result.failure(Exception("El nickname no puede estar vacío"))
        return repository.updateNickname(userId, householdId, newNickname.trim())
    }
}

/**
 * Resolves member profiles from the already-loaded household document — no network. It used to
 * read `/users/{uid}` one by one, which required every profile to stay readable by any
 * authenticated user.
 *
 * A member with no `memberProfiles` entry comes out with blank names, which the UI already renders
 * as "Unknown".
 */
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

/**
 * Fills in the user's public profile copy inside their household. Called on every launch and
 * idempotent: it only writes when the entry is missing or has changed.
 *
 * This is what allows `/users` to be locked down without a migration script — households created
 * before `memberProfiles` existed complete themselves as each member opens the app.
 */
class SyncOwnMemberProfileUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    // userId and householdId are passed separately from the objects on purpose: both documents are
    // deserialised as-is, and their `id` field depends on having been stored at creation time. The
    // session uid and the explicit household id are the reliable sources.
    suspend operator fun invoke(
        householdId: String,
        household: Household,
        userId: String,
        profile: UserProfile
    ): Result<Unit> {
        if (householdId.isBlank() || userId.isBlank()) return Result.success(Unit)

        // The household is already loaded, so comparing here costs no read. Without this guard,
        // being called on every launch would mean one write per user per session — Firestore quota
        // burnt to leave the document exactly as it was.
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

/**
 * Deletes the user account: first their Firestore data, while still authenticated, then the auth
 * account. If Firebase demands a recent login the auth account survives and the error is
 * propagated so the UI can ask them to sign in again.
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
