package com.monsteraltech.habitly.feature.register.domain.model

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val authToken: AuthToken
)
