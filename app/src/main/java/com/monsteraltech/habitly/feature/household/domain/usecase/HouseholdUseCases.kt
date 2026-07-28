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
    /** @return el id de la casa creada. */
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
        // La regla de Firestore ya lo impide, pero comprobarlo aquí evita una escritura
        // condenada a fallar y permite dar un mensaje decente en vez de PERMISSION_DENIED.
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
 * Rellena [Household.ownerId] en las casas creadas antes de que el campo existiera. Solo
 * escribe si está vacío y si quien llama es el primer miembro (quien la creó); las reglas
 * imponen lo mismo en servidor.
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
 * Resuelve los perfiles de los miembros a partir del documento de la casa que ya está
 * cargado: no toca la red. Antes leía `/users/{uid}` uno a uno, lo que exigía dejar todos
 * los perfiles legibles por cualquier usuario autenticado.
 *
 * Un miembro sin entrada en `memberProfiles` (casa anterior al campo, o compañero que
 * todavía no ha abierto la app desde la actualización) sale con los nombres en blanco, que
 * es justo lo que la UI ya traduce como "Desconocido".
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
 * Rellena la copia pública del perfil del usuario dentro de su casa. Se llama en cada
 * arranque y es idempotente: solo escribe si falta o ha cambiado.
 *
 * Es lo que permite cerrar `/users` sin script de migración — las casas creadas antes de
 * que existiera `memberProfiles` se completan solas conforme cada miembro abre la app.
 */
class SyncOwnMemberProfileUseCase @Inject constructor(
    private val repository: HouseholdRepository
) {
    // userId va aparte del perfil a propósito: observeUserProfile deserializa el documento
    // tal cual y su campo `id` depende de que se guardara al crearlo. El uid de sesión es
    // la fuente fiable.
    // householdId llega aparte del objeto por el mismo motivo que userId: el campo `id`
    // del documento podría faltar en casas antiguas, y aquí no vale escribir a ciegas.
    suspend operator fun invoke(
        householdId: String,
        household: Household,
        userId: String,
        profile: UserProfile
    ): Result<Unit> {
        if (householdId.isBlank() || userId.isBlank()) return Result.success(Unit)

        // La casa ya viene cargada, así que comparar aquí no cuesta ninguna lectura. Sin
        // esta guarda, al llamarse en cada arranque sería una escritura por usuario y
        // sesión: cuota de Firestore quemada para dejar el documento igual que estaba.
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
