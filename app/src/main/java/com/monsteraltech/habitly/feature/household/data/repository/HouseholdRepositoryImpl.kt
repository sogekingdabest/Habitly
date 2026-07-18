package com.monsteraltech.habitly.feature.household.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

class HouseholdRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : HouseholdRepository {

    private val secureRandom = SecureRandom()

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val nickname = displayName.split(" ").firstOrNull().orEmpty().ifBlank { displayName }
                val userProfile = UserProfile(
                    id = userId,
                    displayName = displayName,
                    nickname = nickname,
                    activeHouseholdId = ""
                )
                userRef.set(userProfile).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<Unit> {
        return try {
            val newHouseholdId = UUID.randomUUID().toString()
            val inviteCode = generateUniqueInviteCode()

            val household = Household(
                id = newHouseholdId,
                name = householdName.trim().ifBlank { "Casa de $displayName" },
                inviteCode = inviteCode,
                members = listOf(userId),
                customStores = emptyList()
            )

            // 1) Crear la casa (el usuario ya figura como miembro).
            firestore.collection("households").document(newHouseholdId).set(household).await()
            // 2) Marcarla como casa activa del usuario.
            firestore.collection("users").document(userId)
                .update("activeHouseholdId", newHouseholdId).await()
            // 3) Registrar el mapping del código (requiere ser ya miembro, por eso al final).
            registerInviteCode(inviteCode, newHouseholdId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Genera un código legible (sin caracteres ambiguos) usando SecureRandom y
     * garantiza que no exista ya en la colección invite_codes.
     */
    private suspend fun generateUniqueInviteCode(): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = randomCode()
            val exists = firestore.collection("invite_codes").document(candidate).get().await().exists()
            if (!exists) return candidate
        }
        // Fallback improbable: añadimos entropía extra.
        return randomCode() + randomCode().take(2)
    }

    private fun randomCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    private suspend fun registerInviteCode(code: String, householdId: String) {
        firestore.collection("invite_codes").document(code)
            .set(mapOf("householdId" to householdId, "createdAt" to System.currentTimeMillis()))
            .await()
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { listener.remove() }
    }

    override fun observeHousehold(householdId: String): Flow<Household?> = callbackFlow {
        val listener = firestore.collection("households").document(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val household = snapshot?.toObject(Household::class.java)
                trySend(household)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun joinHousehold(userId: String, inviteCode: String): Result<Unit> {
        return try {
            val normalizedCode = inviteCode.uppercase().trim()

            // Resolvemos el código con un get puntual sobre invite_codes (sin query
            // abierta sobre households, que ya no es legible por no-miembros).
            val codeDoc = firestore.collection("invite_codes").document(normalizedCode).get().await()
            val newHouseholdId = codeDoc.getString("householdId")
            if (!codeDoc.exists() || newHouseholdId.isNullOrBlank()) {
                return Result.failure(Exception("Código de invitación no válido"))
            }

            val userRef = firestore.collection("users").document(userId)
            val userSnapshot = userRef.get().await()
            val userProfile = userSnapshot.toObject(UserProfile::class.java)

            val oldHouseholdId = userProfile?.activeHouseholdId

            if (oldHouseholdId == newHouseholdId) {
                return Result.failure(Exception("Ya perteneces a esta casa"))
            }

            val batch = firestore.batch()

            if (!oldHouseholdId.isNullOrBlank()) {
                val oldHouseholdRef = firestore.collection("households").document(oldHouseholdId)
                batch.update(oldHouseholdRef, "members", FieldValue.arrayRemove(userId))
            }

            val targetHouseholdRef = firestore.collection("households").document(newHouseholdId)
            batch.update(targetHouseholdRef, "members", FieldValue.arrayUnion(userId))
            batch.update(userRef, "activeHouseholdId", newHouseholdId)

            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateHouseholdName(householdId: String, newName: String): Result<Unit> {
        return try {
            firestore.collection("households").document(householdId)
                .update("name", newName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateNickname(userId: String, newNickname: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("nickname", newNickname.trim())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMemberProfiles(memberIds: List<String>): Result<List<UserProfile>> {
        return try {
            if (memberIds.isEmpty()) return Result.success(emptyList())
            
            val profiles = memberIds.mapNotNull { memberId ->
                try {
                    val snapshot = firestore.collection("users").document(memberId).get().await()
                    snapshot.toObject(UserProfile::class.java)?.copy(id = memberId)
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            batch.update(
                firestore.collection("households").document(householdId),
                "members", FieldValue.arrayRemove(userId)
            )
            batch.update(
                firestore.collection("users").document(userId),
                "activeHouseholdId", ""
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeMember(householdId: String, memberId: String): Result<Unit> {
        return try {
            // Solo modificamos la lista de members de la casa. El miembro expulsado
            // se autocurará (clearActiveHousehold) al detectar que ya no pertenece.
            firestore.collection("households").document(householdId)
                .update("members", FieldValue.arrayRemove(memberId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun regenerateInviteCode(householdId: String): Result<Unit> {
        return try {
            val householdRef = firestore.collection("households").document(householdId)
            val household = householdRef.get().await().toObject(Household::class.java)
                ?: return Result.failure(Exception("Casa no encontrada"))

            val oldCode = household.inviteCode
            val newCode = generateUniqueInviteCode()

            // 1) Nuevo mapping, 2) actualizar la casa, 3) borrar el mapping antiguo.
            registerInviteCode(newCode, householdId)
            householdRef.update("inviteCode", newCode).await()
            if (oldCode.isNotBlank()) {
                firestore.collection("invite_codes").document(oldCode).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearActiveHousehold(userId: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("activeHouseholdId", "")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteUserData(userId: String): Result<Unit> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            val profile = userRef.get().await().toObject(UserProfile::class.java)

            // 1) Sacar al usuario de su casa actual.
            val householdId = profile?.activeHouseholdId
            if (!householdId.isNullOrBlank()) {
                firestore.collection("households").document(householdId)
                    .update("members", FieldValue.arrayRemove(userId))
                    .await()
            }

            // 2) Borrar sus rutinas personales (subcolección).
            val routines = userRef.collection("routines").get().await()
            if (!routines.isEmpty) {
                val batch = firestore.batch()
                routines.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            // 3) Borrar el documento de perfil.
            userRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        // Alfabeto sin caracteres ambiguos (sin I, O, 0, 1).
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
        private const val MAX_CODE_ATTEMPTS = 5
    }
}
