package com.monsteraltech.habitly.feature.login.domain.repository

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials

interface AuthRepository {

    // — Existente —
    suspend fun login(credentials: LoginCredentials): Result<AuthToken>

    // — Registro con email/contraseña —
    suspend fun register(credentials: RegisterCredentials): Result<AuthUser>

    // — Google Sign-In —
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    // — Estado de sesión —
    fun getCurrentUser(): AuthUser?
    suspend fun reloadCurrentUser(): Result<AuthUser>
    suspend fun signOut()
}
