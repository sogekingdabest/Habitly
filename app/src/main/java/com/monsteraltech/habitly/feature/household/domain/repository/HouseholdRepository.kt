package com.monsteraltech.habitly.feature.household.domain.repository

import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    /**
     * Crea el perfil del usuario si no existe, SIN casa asociada
     * (activeHouseholdId vacío). El onboarding decide luego crear o unirse.
     */
    suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit>

    /**
     * Crea una nueva casa para el usuario y la marca como activa.
     */
    suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<Unit>

    /**
     * Observa el perfil del usuario para obtener su activeHouseholdId
     */
    fun observeUserProfile(userId: String): Flow<UserProfile?>

    /**
     * Observa la casa actual
     */
    fun observeHousehold(householdId: String): Flow<Household?>

    /**
     * Permite a un usuario unirse a una casa mediante un código de invitación
     */
    suspend fun joinHousehold(userId: String, inviteCode: String): Result<Unit>

    /**
     * Permite editar el nombre de la casa
     */
    suspend fun updateHouseholdName(householdId: String, newName: String): Result<Unit>

    /**
     * Actualiza el nickname del usuario
     */
    suspend fun updateNickname(userId: String, newNickname: String): Result<Unit>

    /**
     * Obtiene los perfiles de una lista de miembros
     */
    suspend fun getMemberProfiles(memberIds: List<String>): Result<List<UserProfile>>

    /**
     * El usuario sale de su casa actual (se elimina de members y su
     * activeHouseholdId queda vacío, volviendo al onboarding).
     */
    suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit>

    /**
     * Expulsa a un miembro de la casa (solo modifica la lista de members).
     */
    suspend fun removeMember(householdId: String, memberId: String): Result<Unit>

    /**
     * Genera un nuevo código de invitación para la casa e invalida el anterior.
     */
    suspend fun regenerateInviteCode(householdId: String): Result<Unit>

    /**
     * Reinicia la casa activa del usuario a vacío (autocuración cuando ha sido
     * expulsado y su perfil aún apunta a una casa de la que ya no es miembro).
     */
    suspend fun clearActiveHousehold(userId: String): Result<Unit>

    /**
     * Borra los datos de Firestore del usuario: lo saca de su casa, elimina sus
     * rutinas personales y borra su documento de perfil. Se llama antes de eliminar
     * la cuenta de autenticación.
     */
    suspend fun deleteUserData(userId: String): Result<Unit>
}
