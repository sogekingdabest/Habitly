package com.monsteraltech.habitly.feature.household.domain.repository

import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * El código no sirve: no existe, ya ha caducado o apunta a una casa que no está. Los tres
 * casos se funden en uno a propósito, para no revelar qué códigos existen a quien los vaya
 * probando.
 */
class InvalidInviteCodeException :
    Exception("El código de invitación no es válido o ha caducado")

interface HouseholdRepository {
    /**
     * Crea el perfil del usuario si no existe, SIN casa asociada
     * (activeHouseholdId vacío). El onboarding decide luego crear o unirse.
     */
    suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit>

    /**
     * Crea una nueva casa para el usuario y la marca como activa.
     */
    /** Crea la casa, la marca como activa del usuario y devuelve **su id**. */
    suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<String>

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
     * Actualiza el nickname del usuario y lo propaga a la copia pública que vive en el
     * documento de su casa. [householdId] en blanco (usuario aún sin casa) omite la
     * propagación.
     */
    suspend fun updateNickname(userId: String, householdId: String, newNickname: String): Result<Unit>

    /**
     * Escribe la entrada de [userId] en el `memberProfiles` de la casa. Idempotente y
     * pensada para llamarse en cada arranque: rellena las casas creadas antes de que
     * existiera el campo sin necesidad de un script de migración.
     */
    suspend fun syncOwnMemberProfile(
        householdId: String,
        userId: String,
        displayName: String,
        nickname: String
    ): Result<Unit>

    /**
     * Fija [userId] como propietario de la casa. Solo tiene efecto en casas creadas antes
     * de que existiera el campo (las reglas únicamente admiten rellenarlo si está vacío y
     * si quien lo reclama es el primer miembro, es decir, quien la creó).
     */
    suspend fun claimHouseholdOwnership(householdId: String, userId: String): Result<Unit>

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
