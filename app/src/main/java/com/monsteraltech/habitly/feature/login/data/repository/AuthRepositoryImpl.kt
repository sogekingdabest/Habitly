package com.monsteraltech.habitly.feature.login.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
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
    private val dataStore: DataStore<Preferences>
) : AuthRepository {

    companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }

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

            persistToken(accessToken, "")
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

            persistToken(accessToken, "")

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

            persistToken(accessToken, "")

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
        firebaseAuth.signOut()
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun persistToken(accessToken: String, refreshToken: String) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
        }
    }
}
