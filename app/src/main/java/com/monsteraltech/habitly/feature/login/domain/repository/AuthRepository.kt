package com.monsteraltech.habitly.feature.login.domain.repository

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials

interface AuthRepository {

    // — Login —
    suspend fun login(credentials: LoginCredentials): Result<AuthToken>

    // — Email/password registration —
    suspend fun register(credentials: RegisterCredentials): Result<AuthUser>

    // — Google Sign-In —
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    // — Session state —
    fun getCurrentUser(): AuthUser?
    suspend fun reloadCurrentUser(): Result<AuthUser>
    suspend fun signOut()

    // — Password recovery —
    /**
     * Sends a password-reset email. For privacy (avoiding account enumeration) it returns success
     * even when the address matches no user; it only fails on real errors (invalid format, network).
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    // — Email verification —
    /** Resends the verification email to the currently authenticated user. */
    suspend fun resendVerificationEmail(): Result<Unit>

    // — Account deletion —
    suspend fun deleteAccount(): Result<Unit>
}
