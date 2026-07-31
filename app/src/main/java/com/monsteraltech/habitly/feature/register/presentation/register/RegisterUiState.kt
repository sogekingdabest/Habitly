package com.monsteraltech.habitly.feature.register.presentation.register

import com.monsteraltech.habitly.feature.register.domain.model.RegisterError

data class RegisterUiState(

    // — Form fields —
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    // — Visibility of protected fields —
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,

    // — Per-field errors (null = no visible error) —
    val displayNameError: RegisterError? = null,
    val emailError: RegisterError? = null,
    val passwordError: RegisterError? = null,
    val confirmPasswordError: RegisterError? = null,

    // — Loading state —
    val isLoading: Boolean = false,
    val isGoogleSignInLoading: Boolean = false,

    // — Global error (network errors, email already in use, etc.) —
    val globalError: RegisterError? = null,

    // — Main button enablement (quick heuristic: fields non-empty) —
    val isRegisterButtonEnabled: Boolean = false,
)
