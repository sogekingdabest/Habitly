package com.monsteraltech.habitly.feature.register.domain.model

data class RegisterCredentials(
    val email: String,
    val password: String,
    val confirmPassword: String,
    val displayName: String
)
