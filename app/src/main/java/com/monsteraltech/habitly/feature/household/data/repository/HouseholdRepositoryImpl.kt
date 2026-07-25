package com.monsteraltech.habitly.feature.household.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.model.MemberProfile
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.household.domain.repository.InvalidInviteCodeException
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

    override suspend fun createHousehold(userId: String, displayName: String, householdName: String): Result<String> {
        return try {
            val newHouseholdId = UUID.randomUUID().toString()
            val inviteCode = generateUniqueInviteCode()
            val expiresAt = newInviteExpiry()

            // El nickname por defecto es el mismo que calcula ensureUserProfile.
            val nickname = displayName.split(" ").firstOrNull().orEmpty().ifBlank { displayName }

            val household = Household(
                id = newHouseholdId,
                name = householdName.trim().ifBlank { "Casa de $displayName" },
                inviteCode = inviteCode,
                inviteCodeExpiresAt = expiresAt,
                ownerId = userId,
                members = listOf(userId),
                customStores = emptyList(),
                memberProfiles = mapOf(
                    userId to MemberProfile(displayName = displayName, nickname = nickname)
                )
            )

            // 1) Crear la casa (el usuario ya figura como miembro).
            firestore.collection("households").document(newHouseholdId).set(household).await()
            // 2) Marcarla como casa activa del usuario.
            firestore.collection("users").document(userId)
                .update("activeHouseholdId", newHouseholdId).await()
            // 3) Registrar el mapping del código (requiere ser ya miembro, por eso al final).
            registerInviteCode(inviteCode, newHouseholdId, expiresAt)

            // El id vuelve al llamante: el onboarding lo necesita para crear ahí las rutinas
            // de las plantillas sin tener que releer el perfil recién escrito.
            Result.success(newHouseholdId)
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

    /** Momento en que caducaría un código emitido ahora (epoch ms). */
    private fun newInviteExpiry(): Long = System.currentTimeMillis() + INVITE_CODE_TTL_MS

    private suspend fun registerInviteCode(code: String, householdId: String, expiresAt: Long) {
        firestore.collection("invite_codes").document(code)
            .set(
                mapOf(
                    "householdId" to householdId,
                    "createdAt" to System.currentTimeMillis(),
                    // Un código sin caducidad es una llave permanente: quien salga de la
                    // casa (o a quien expulsen) podría volver a entrar meses después con el
                    // código que memorizó. La regla de lectura valida este campo.
                    "expiresAt" to expiresAt
                )
            )
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
            //
            // La regla de lectura exige que el código no haya caducado, así que uno vencido
            // llega aquí como PERMISSION_DENIED y uno inexistente como documento vacío. Los
            // dos acaban en el mismo InvalidInviteCodeException: ni uno ni otro devuelven
            // householdId, y de cara al usuario el mensaje es idéntico.
            val codeDoc = runCatching {
                firestore.collection("invite_codes").document(normalizedCode).get().await()
            }.getOrNull()

            val newHouseholdId = codeDoc?.getString("householdId")
            if (codeDoc?.exists() != true || newHouseholdId.isNullOrBlank()) {
                return Result.failure(InvalidInviteCodeException())
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
                batch.update(
                    oldHouseholdRef,
                    "members", FieldValue.arrayRemove(userId),
                    memberProfilePath(userId), FieldValue.delete()
                )
            }

            // members y memberProfiles se escriben JUNTOS y en la misma operación: la regla
            // de auto-unión exige que la entrada añadida a memberProfiles sea la del propio
            // usuario, y si llegaran en escrituras separadas la primera sería rechazada.
            val targetHouseholdRef = firestore.collection("households").document(newHouseholdId)
            batch.update(
                targetHouseholdRef,
                "members", FieldValue.arrayUnion(userId),
                memberProfilePath(userId), MemberProfile(
                    displayName = userProfile?.displayName.orEmpty(),
                    nickname = userProfile?.nickname.orEmpty()
                )
            )
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

    override suspend fun updateNickname(
        userId: String,
        householdId: String,
        newNickname: String
    ): Result<Unit> {
        return try {
            val nickname = newNickname.trim()
            firestore.collection("users").document(userId)
                .update("nickname", nickname)
                .await()

            // La copia pública del nombre vive en la casa; sin esto, los demás miembros
            // seguirían viendo el nickname antiguo hasta la siguiente sincronización.
            if (householdId.isNotBlank()) {
                firestore.collection("households").document(householdId)
                    .update(FieldPath.of(MEMBER_PROFILES, userId, "nickname"), nickname)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncOwnMemberProfile(
        householdId: String,
        userId: String,
        displayName: String,
        nickname: String
    ): Result<Unit> {
        return try {
            // Escritura a secas: decidir SI hace falta escribir es cosa de
            // SyncOwnMemberProfileUseCase, que ya tiene la casa cargada y así se ahorra
            // una lectura de Firestore en cada arranque.
            firestore.collection("households").document(householdId)
                .update(
                    memberProfilePath(userId),
                    MemberProfile(displayName = displayName, nickname = nickname)
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun claimHouseholdOwnership(householdId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("households").document(householdId)
                .update("ownerId", userId)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveHousehold(userId: String, householdId: String): Result<Unit> {
        return try {
            val householdRef = firestore.collection("households").document(householdId)
            val household = householdRef.get().await().toObject(Household::class.java)
            val heir = successorIfOwnerLeaves(household, userId)

            // Antes de salir, mientras aún somos miembros (crear el mapping del código
            // nuevo exige serlo): el código que nos llevamos en la cabeza deja de valer.
            // Si falla, salir sigue siendo lo prioritario.
            if (household != null && household.members.size > 1) {
                runCatching { rotateInviteCode(householdId) }
            }

            val batch = firestore.batch()
            if (heir != null) {
                // Si se va el propietario, la casa se queda sin nadie que pueda expulsar
                // ni borrar. El traspaso al siguiente miembro evita ese limbo.
                batch.update(
                    householdRef,
                    "members", FieldValue.arrayRemove(userId),
                    memberProfilePath(userId), FieldValue.delete(),
                    "ownerId", heir
                )
            } else {
                batch.update(
                    householdRef,
                    "members", FieldValue.arrayRemove(userId),
                    memberProfilePath(userId), FieldValue.delete()
                )
            }
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

    /**
     * A quién pasa la propiedad si [leavingUserId] se va. Null si no era el propietario o
     * si no queda nadie a quien traspasarla (la casa se queda vacía y da igual).
     */
    private fun successorIfOwnerLeaves(household: Household?, leavingUserId: String): String? {
        if (household == null || !household.isOwner(leavingUserId)) return null
        return household.members.firstOrNull { it != leavingUserId }
    }

    override suspend fun removeMember(householdId: String, memberId: String): Result<Unit> {
        return try {
            // Solo modificamos la casa (members y su perfil público). El miembro expulsado
            // se autocurará (clearActiveHousehold) al detectar que ya no pertenece.
            firestore.collection("households").document(householdId)
                .update(
                    "members", FieldValue.arrayRemove(memberId),
                    memberProfilePath(memberId), FieldValue.delete()
                )
                .await()

            // Expulsar sin rotar el código no expulsa a nadie: se volvería a unir con el
            // mismo código. Si la rotación falla, la expulsión ya está hecha y es lo que
            // el usuario ha pedido; el código se puede regenerar a mano.
            runCatching { rotateInviteCode(householdId) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun regenerateInviteCode(householdId: String): Result<Unit> {
        return try {
            rotateInviteCode(householdId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sustituye el código de la casa por uno nuevo e invalida el anterior. Se llama al
     * regenerarlo a mano y también cuando alguien sale o es expulsado: si no, el código que
     * esa persona conoce le seguiría abriendo la puerta.
     */
    private suspend fun rotateInviteCode(householdId: String) {
        val householdRef = firestore.collection("households").document(householdId)
        val household = householdRef.get().await().toObject(Household::class.java)
            ?: throw IllegalStateException("Casa no encontrada")

        val oldCode = household.inviteCode
        val newCode = generateUniqueInviteCode()

        // 1) Nuevo mapping, 2) actualizar la casa, 3) borrar el mapping antiguo.
        val expiresAt = newInviteExpiry()
        registerInviteCode(newCode, householdId, expiresAt)
        householdRef.update("inviteCode", newCode, "inviteCodeExpiresAt", expiresAt).await()
        if (oldCode.isNotBlank()) {
            firestore.collection("invite_codes").document(oldCode).delete().await()
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

            // 1) Sacar al usuario de su casa actual, incluida su copia pública del perfil:
            //    si se quedara, el nombre de una cuenta borrada seguiría visible para el
            //    resto. Y si era el propietario, traspasar antes de desaparecer.
            val householdId = profile?.activeHouseholdId
            if (!householdId.isNullOrBlank()) {
                val householdRef = firestore.collection("households").document(householdId)
                val household = householdRef.get().await().toObject(Household::class.java)
                val heir = successorIfOwnerLeaves(household, userId)

                if (heir != null) {
                    householdRef.update(
                        "members", FieldValue.arrayRemove(userId),
                        memberProfilePath(userId), FieldValue.delete(),
                        "ownerId", heir
                    ).await()
                } else {
                    householdRef.update(
                        "members", FieldValue.arrayRemove(userId),
                        memberProfilePath(userId), FieldValue.delete()
                    ).await()
                }
            }

            // 2) Borrar sus rutinas personales. Firestore NO borra subcolecciones en cascada:
            //    hay que recoger también los completions de cada rutina o quedarían huérfanos
            //    (datos personales retenidos tras borrar la cuenta).
            val routines = userRef.collection("routines").get().await()
            val refsToDelete = mutableListOf<DocumentReference>()
            for (routine in routines.documents) {
                val completions = routine.reference.collection("completions").get().await()
                completions.documents.forEach { refsToDelete.add(it.reference) }
                refsToDelete.add(routine.reference)
            }
            // Un batch admite 500 operaciones como máximo; se trocea con margen.
            refsToDelete.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            // 3) Borrar el documento de perfil.
            userRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ruta al perfil público de un miembro. Se construye con [FieldPath] en vez de con una
     * cadena "memberProfiles.$uid" porque en una cadena el punto separa niveles: un uid con
     * un carácter especial partiría la ruta y escribiría en el sitio equivocado.
     */
    private fun memberProfilePath(userId: String): FieldPath =
        FieldPath.of(MEMBER_PROFILES, userId)

    companion object {
        private const val MEMBER_PROFILES = "memberProfiles"

        /** Vida de un código de invitación. Se puede regenerar a mano cuando haga falta. */
        private const val INVITE_CODE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        // Alfabeto sin caracteres ambiguos (sin I, O, 0, 1).
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
        private const val MAX_CODE_ATTEMPTS = 5

        // Límite real de Firestore: 500 operaciones por batch; margen por si acaso.
        private const val MAX_BATCH_OPS = 450
    }
}
