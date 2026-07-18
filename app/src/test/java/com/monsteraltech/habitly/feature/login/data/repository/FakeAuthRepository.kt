package com.monsteraltech.habitly.feature.login.data.repository

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials

/**
 * Fake reutilizable para tests unitarios.
 * Sigue el mismo patrón de fake configurable del proyecto (ver LoginWithEmailUseCaseTest).
 *
 * Uso:
 *   val fakeRepo = FakeAuthRepository()
 *   fakeRepo.willFail = true           // simula error de red
 *   fakeRepo.token = AuthToken(...)    // controla el token devuelto
 */
class FakeAuthRepository : AuthRepository {

    var willFail: Boolean = false
    var errorMessage: String = "Error de red simulado"
    var token: AuthToken = AuthToken(accessToken = "fake_access", refreshToken = "fake_refresh")
    var stubCurrentUser: AuthUser? = null

    val loginCallCount get() = _loginCallCount
    private var _loginCallCount = 0

    val registerCallCount get() = _registerCallCount
    private var _registerCallCount = 0

    val googleSignInCallCount get() = _googleSignInCallCount
    private var _googleSignInCallCount = 0

    override suspend fun login(credentials: LoginCredentials): Result<AuthToken> {
        _loginCallCount++
        return if (willFail) {
            Result.failure(Exception(errorMessage))
        } else {
            Result.success(token)
        }
    }

    override suspend fun register(credentials: RegisterCredentials): Result<AuthUser> {
        _registerCallCount++
        return if (willFail) {
            Result.failure(Exception(errorMessage))
        } else {
            val user = AuthUser(
                uid = "fake_uid",
                email = credentials.email,
                displayName = credentials.displayName,
                isEmailVerified = false,
                authToken = token
            )
            stubCurrentUser = user
            Result.success(user)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        _googleSignInCallCount++
        return if (willFail) {
            Result.failure(Exception(errorMessage))
        } else {
            val user = AuthUser(
                uid = "google_uid",
                email = "google@test.com",
                displayName = "Google User",
                isEmailVerified = true,
                authToken = token
            )
            stubCurrentUser = user
            Result.success(user)
        }
    }

    override fun getCurrentUser(): AuthUser? = stubCurrentUser

    override suspend fun reloadCurrentUser(): Result<AuthUser> {
        return stubCurrentUser?.let { Result.success(it) } 
            ?: Result.failure(Exception("No user logged in"))
    }

    override suspend fun signOut() {
        stubCurrentUser = null
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return if (willFail) {
            Result.failure(Exception(errorMessage))
        } else {
            stubCurrentUser = null
            Result.success(Unit)
        }
    }

    fun reset() {
        willFail = false
        errorMessage = "Error de red simulado"
        token = AuthToken(accessToken = "fake_access", refreshToken = "fake_refresh")
        stubCurrentUser = null
        _loginCallCount = 0
        _registerCallCount = 0
        _googleSignInCallCount = 0
    }
}
