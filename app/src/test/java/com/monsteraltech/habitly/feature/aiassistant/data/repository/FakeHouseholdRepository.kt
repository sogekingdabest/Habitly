package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.household.domain.model.Household
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeHouseholdRepository : HouseholdRepository {

    var stubProfile: UserProfile? = null

    /** Id que devuelve [createHousehold] (el onboarding lo usa para crear ahí las plantillas). */
    var createdHouseholdId = "casa-nueva"

    /** Casas creadas: (userId, nombre). */
    val createdHouseholds = mutableListOf<Pair<String, String>>()

    /** Si [createHousehold] debe fallar, para probar el camino de error del onboarding. */
    var failCreateHousehold = false

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> = Result.success(Unit)

    override suspend fun createHousehold(
        userId: String,
        displayName: String,
        householdName: String
    ): Result<String> {
        if (failCreateHousehold) return Result.failure(Exception("No se pudo crear la casa"))
        createdHouseholds += userId to householdName
        return Result.success(createdHouseholdId)
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = flowOf(stubProfile)

    override fun observeHousehold(householdId: String): Flow<Household?> = flowOf(null)

    override suspend fun joinHousehold(userId: String, inviteCode: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateHouseholdName(householdId: String, newName: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateNickname(
        userId: String,
        householdId: String,
        newNickname: String
    ): Result<Unit> = Result.success(Unit)

    /** Escrituras de perfil recibidas, para poder afirmar que NO se escribe de más. */
    val syncedProfiles = mutableListOf<Triple<String, String, String>>()

    override suspend fun syncOwnMemberProfile(
        householdId: String,
        userId: String,
        displayName: String,
        nickname: String
    ): Result<Unit> {
        syncedProfiles += Triple(householdId, userId, nickname)
        return Result.success(Unit)
    }

    /** Traspasos de propiedad recibidos, para comprobar que solo ocurren cuando toca. */
    val ownershipClaims = mutableListOf<Pair<String, String>>()

    override suspend fun claimHouseholdOwnership(householdId: String, userId: String): Result<Unit> {
        ownershipClaims += householdId to userId
        return Result.success(Unit)
    }

    override suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit> = Result.success(Unit)

    /** Expulsiones recibidas, para comprobar que un no-propietario no llega a escribir. */
    val removedMembers = mutableListOf<Pair<String, String>>()

    override suspend fun removeMember(householdId: String, memberId: String): Result<Unit> {
        removedMembers += householdId to memberId
        return Result.success(Unit)
    }

    override suspend fun regenerateInviteCode(householdId: String): Result<Unit> = Result.success(Unit)

    override suspend fun clearActiveHousehold(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteUserData(userId: String): Result<Unit> = Result.success(Unit)

    fun reset() {
        stubProfile = null
        createdHouseholdId = "casa-nueva"
        createdHouseholds.clear()
        failCreateHousehold = false
        syncedProfiles.clear()
        ownershipClaims.clear()
        removedMembers.clear()
    }
}
