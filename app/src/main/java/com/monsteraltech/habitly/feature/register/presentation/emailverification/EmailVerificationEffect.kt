package com.monsteraltech.habitly.feature.register.presentation.emailverification

sealed class EmailVerificationEffect {
    object NavigateToHome : EmailVerificationEffect()
    object NavigateToRegister : EmailVerificationEffect()
    data class ShowSnackbar(val message: String) : EmailVerificationEffect()
}
