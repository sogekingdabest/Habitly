package com.monsteraltech.habitly.feature.household.domain.repository

import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    /**
     * Comprueba si el usuario tiene perfil. Si no, crea el perfil y una casa por defecto.
     */
    suspend fun initializeUserAndHousehold(userId: String, displayName: String): Result<Unit>
    
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
}
