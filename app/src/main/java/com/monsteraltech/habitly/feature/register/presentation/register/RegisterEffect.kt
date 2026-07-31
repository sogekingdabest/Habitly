package com.monsteraltech.habitly.feature.register.presentation.register

sealed class RegisterEffect {

    // — Navigation —
    object NavigateToEmailVerification : RegisterEffect()
    object NavigateToHome : RegisterEffect()
    object NavigateToLogin : RegisterEffect()

    // — Google Sign-In —
    object LaunchGoogleSignIn : RegisterEffect()

    // — User feedback —
    data class ShowSnackbar(val message: String) : RegisterEffect()
}
