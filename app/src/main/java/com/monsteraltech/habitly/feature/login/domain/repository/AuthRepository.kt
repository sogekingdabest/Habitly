package com.monsteraltech.habitly.feature.login.domain.repository

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials

interface AuthRepository {

    suspend fun login(credentials: LoginCredentials): Result<AuthToken>

    suspend fun register(credentials: RegisterCredentials): Result<AuthUser>

    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    fun getCurrentUser(): AuthUser?
    suspend fun reloadCurrentUser(): Result<AuthUser>
    suspend fun signOut()

    /**
     * Sends a password reset email.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Resends the verification email to the currently authenticated user. */
    suspend fun resendVerificationEmail(): Result<Unit>

    // — Borrado de cuenta —
    suspend fun deleteAccount(): Result<Unit>
}
