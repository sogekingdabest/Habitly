package com.monsteraltech.habitly.feature.login.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.login.domain.model.ReauthenticationRequiredException
import com.monsteraltech.habitly.feature.login.domain.account.AccountDataCleaner
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val accountDataCleaners: Set<@JvmSuppressWildcards AccountDataCleaner>
) : AuthRepository {

    // ─────────────────────────────────────────────────────────────────────────
    // Existente: Login con email/contraseña
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun login(credentials: LoginCredentials): Result<AuthToken> {
        return try {
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(credentials.email, credentials.password)
                .await()
            val user = authResult.user
                ?: throw Exception("Usuario no encontrado tras el acceso")

            val tokenResult = user.getIdToken(false).await()
            val accessToken = tokenResult.token ?: ""
            val authToken = AuthToken(accessToken = accessToken, refreshToken = "")

            Result.success(authToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nuevo: Registro con email/contraseña
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun register(credentials: RegisterCredentials): Result<AuthUser> {
        return try {
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(credentials.email, credentials.password)
                .await()
            val user = authResult.user
                ?: return Result.failure(RegisterError.Unknown(Exception("FirebaseUser null tras registro")))

            // Actualizar displayName en el perfil de Firebase
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(credentials.displayName)
                .build()
            user.updateProfile(profileUpdates).await()

            // Enviar email de verificación
            user.sendEmailVerification().await()

            // Obtener token de sesión
            val tokenResult = user.getIdToken(false).await()
            val accessToken = tokenResult.token ?: ""
            val authToken = AuthToken(accessToken = accessToken, refreshToken = "")

            Result.success(
                AuthUser(
                    uid = user.uid,
                    email = user.email,
                    displayName = credentials.displayName,
                    isEmailVerified = user.isEmailVerified,
                    authToken = authToken
                )
            )
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(RegisterError.EmailAlreadyInUse)
        } catch (e: UnknownHostException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: IOException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: Exception) {
            Result.failure(RegisterError.Unknown(e))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nuevo: Google Sign-In
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: return Result.failure(RegisterError.Unknown(Exception("FirebaseUser null tras Google Sign-In")))

            val tokenResult = user.getIdToken(false).await()
            val accessToken = tokenResult.token ?: ""
            val authToken = AuthToken(accessToken = accessToken, refreshToken = "")

            Result.success(
                AuthUser(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    isEmailVerified = true, // Google garantiza verificación
                    authToken = authToken
                )
            )
        } catch (e: UnknownHostException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: IOException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: Exception) {
            Result.failure(RegisterError.Unknown(e))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nuevo: Estado de sesión
    // ─────────────────────────────────────────────────────────────────────────

    override fun getCurrentUser(): AuthUser? {
        val user = firebaseAuth.currentUser ?: return null
        return AuthUser(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            isEmailVerified = user.isEmailVerified,
            authToken = AuthToken(accessToken = "", refreshToken = "")
        )
    }

    override suspend fun reloadCurrentUser(): Result<AuthUser> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(RegisterError.Unknown(Exception("No hay usuario activo")))
            user.reload().await() // invalida la caché local de Firebase
            Result.success(
                AuthUser(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    isEmailVerified = user.isEmailVerified,
                    authToken = AuthToken(accessToken = "", refreshToken = "")
                )
            )
        } catch (e: Exception) {
            Result.failure(RegisterError.Unknown(e))
        }
    }

    override suspend fun signOut() {
        // Solo cierra la sesión de Firebase. NO se borran datos locales: un logout (voluntario
        // o por caducidad de sesión) no debe llevarse el historial de chat; el usuario volvería
        // a entrar y lo encontraría vacío. La limpieza vive en deleteAccount().
        firebaseAuth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            // El correo no está registrado. No lo revelamos (evita enumeración de cuentas):
            // se responde igual que un envío correcto.
            Result.success(Unit)
        } catch (e: UnknownHostException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: IOException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(Exception("No hay usuario activo"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: UnknownHostException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: IOException) {
            Result.failure(RegisterError.NetworkError)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(Exception("No hay usuario activo"))
            user.delete().await()
            clearLocalAccountData()
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(ReauthenticationRequiredException())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ejecuta todos los [AccountDataCleaner] registrados al borrar la cuenta. Cada uno aislado:
     * que una limpieza falle no debe impedir el borrado ni frenar al resto.
     */
    private suspend fun clearLocalAccountData() {
        accountDataCleaners.forEach { cleaner ->
            try {
                cleaner.clearAccountData()
            } catch (e: Exception) {
                Log.w("AuthRepository", "Fallo limpiando datos locales de la cuenta", e)
            }
        }
    }
}
