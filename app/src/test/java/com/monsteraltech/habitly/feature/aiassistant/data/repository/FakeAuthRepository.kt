package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials

class FakeAuthRepository : AuthRepository {

    var stubCurrentUser: AuthUser? = null

    override suspend fun login(credentials: LoginCredentials): Result<AuthToken> = Result.success(AuthToken("fake", "fake"))

    override suspend fun register(credentials: RegisterCredentials): Result<AuthUser> = Result.success(stubCurrentUser ?: AuthUser("uid", "test@test.com", "Test", true, AuthToken("fake", "fake")))

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = Result.success(AuthUser("google_uid", "google@test.com", "Google", true, AuthToken("fake", "fake")))

    override fun getCurrentUser(): AuthUser? = stubCurrentUser

    override suspend fun reloadCurrentUser(): Result<AuthUser> = stubCurrentUser?.let { Result.success(it) } ?: Result.failure(Exception("No user"))

    override suspend fun signOut() {
        stubCurrentUser = null
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun resendVerificationEmail(): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAccount(): Result<Unit> {
        stubCurrentUser = null
        return Result.success(Unit)
    }

    fun reset() {
        stubCurrentUser = null
    }
}
