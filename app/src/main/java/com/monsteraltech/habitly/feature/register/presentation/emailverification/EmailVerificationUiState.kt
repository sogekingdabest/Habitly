package com.monsteraltech.habitly.feature.register.presentation.emailverification

import com.monsteraltech.habitly.feature.register.domain.model.RegisterError

data class EmailVerificationUiState(
    val userEmail: String = "",
    val isCheckingVerification: Boolean = false,
    val isResendingEmail: Boolean = false,
    val isVerified: Boolean = false,
    val error: RegisterError? = null,
    val resendCooldownSeconds: Int = 0
)
