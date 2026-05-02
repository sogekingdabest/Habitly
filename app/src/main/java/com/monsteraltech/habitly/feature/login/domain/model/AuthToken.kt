package com.monsteraltech.habitly.feature.login.domain.model

data class AuthToken(
    val accessToken: String,
    val refreshToken: String
)
