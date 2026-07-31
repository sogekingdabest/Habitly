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
    // Email/password login
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
    // Email/password registration
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun register(credentials: RegisterCredentials): Result<AuthUser> {
        return try {
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(credentials.email, credentials.password)
                .await()
            val user = authResult.user
                ?: return Result.failure(RegisterError.Unknown(Exception("FirebaseUser null tras registro")))

            // Set displayName on the Firebase profile.
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(credentials.displayName)
                .build()
            user.updateProfile(profileUpdates).await()

            // Send the verification email.
            user.sendEmailVerification().await()

            // Get the session token.
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
    // Google Sign-In
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
                    isEmailVerified = true, // Google guarantees verification
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
    // Session state
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
            user.reload().await() // invalidates Firebase's local cache
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
        // Only closes the Firebase session. Local data is NOT wiped: a logout (voluntary or from
        // session expiry) must not take the chat history with it; the user would sign back in and
        // find it empty. The cleanup lives in deleteAccount().
        firebaseAuth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            // The email is not registered. We do not reveal it (avoids account enumeration): the
            // response is the same as a successful send.
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
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs every registered [AccountDataCleaner] on account deletion. Each is isolated: one cleanup
     * failing must neither block the deletion nor stop the rest.
     */
    private suspend fun clearLocalAccountData() {
        accountDataCleaners.forEach { cleaner ->
            try {
                cleaner.clearAccountData()
            } catch (e: Exception) {
                Log.w("AuthRepository", "Failed cleaning up local account data", e)
            }
        }
    }
}
