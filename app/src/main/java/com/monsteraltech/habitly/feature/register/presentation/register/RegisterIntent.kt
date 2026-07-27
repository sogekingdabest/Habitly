package com.monsteraltech.habitly.feature.register.presentation.register

sealed class RegisterIntent {
    data class DisplayNameChanged(val value: String) : RegisterIntent()
    data class EmailChanged(val value: String) : RegisterIntent()
    data class PasswordChanged(val value: String) : RegisterIntent()
    data class ConfirmPasswordChanged(val value: String) : RegisterIntent()

    object TogglePasswordVisibility : RegisterIntent()
    object ToggleConfirmPasswordVisibility : RegisterIntent()

    object RegisterWithEmailClicked : RegisterIntent()
    object SignInWithGoogleClicked : RegisterIntent()

    data class GoogleIdTokenReceived(val idToken: String) : RegisterIntent()
    object GoogleSignInCancelled : RegisterIntent()
    object GoogleSignInFailed : RegisterIntent()

    object NavigateToLoginClicked : RegisterIntent()
    object ErrorDismissed : RegisterIntent()
}
