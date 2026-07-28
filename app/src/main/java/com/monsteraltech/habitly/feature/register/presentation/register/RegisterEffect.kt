package com.monsteraltech.habitly.feature.register.presentation.register

sealed class RegisterEffect {

    // — Navegación —
    object NavigateToEmailVerification : RegisterEffect()
    object NavigateToHome : RegisterEffect()
    object NavigateToLogin : RegisterEffect()

    // — Google Sign-In —
    object LaunchGoogleSignIn : RegisterEffect()

    // — Feedback —
    data class ShowSnackbar(val message: String) : RegisterEffect()
}
