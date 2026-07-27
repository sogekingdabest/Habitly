package com.monsteraltech.habitly.feature.register.presentation.register

import com.monsteraltech.habitly.feature.register.domain.model.RegisterError

data class RegisterUiState(

    // — Campos del formulario —
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    // — Visibilidad de campos protegidos —
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,

    // — Errores por campo (null = sin error visible) —
    val displayNameError: RegisterError? = null,
    val emailError: RegisterError? = null,
    val passwordError: RegisterError? = null,
    val confirmPasswordError: RegisterError? = null,

    // — Estado de carga —
    val isLoading: Boolean = false,
    val isGoogleSignInLoading: Boolean = false,
    val globalError: RegisterError? = null,
    val isRegisterButtonEnabled: Boolean = false,
)
