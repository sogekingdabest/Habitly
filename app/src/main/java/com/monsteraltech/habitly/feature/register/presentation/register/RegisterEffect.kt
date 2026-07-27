package com.monsteraltech.habitly.feature.register.presentation.register

sealed class RegisterEffect {

    object NavigateToEmailVerification : RegisterEffect()
    object NavigateToHome : RegisterEffect()
    object NavigateToLogin : RegisterEffect()

    object LaunchGoogleSignIn : RegisterEffect()

    data class ShowSnackbar(val message: String) : RegisterEffect()
}
