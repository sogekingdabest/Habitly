package com.monsteraltech.habitly.feature.register.presentation.register

sealed class RegisterIntent {

    // — Field changes —
    data class DisplayNameChanged(val value: String) : RegisterIntent()
    data class EmailChanged(val value: String) : RegisterIntent()
    data class PasswordChanged(val value: String) : RegisterIntent()
    data class ConfirmPasswordChanged(val value: String) : RegisterIntent()

    // — Password visibility —
    object TogglePasswordVisibility : RegisterIntent()
    object ToggleConfirmPasswordVisibility : RegisterIntent()

    // — Main actions —
    object RegisterWithEmailClicked : RegisterIntent()
    object SignInWithGoogleClicked : RegisterIntent()

    // — Credential Manager result (dispatched from the UI) —
    data class GoogleIdTokenReceived(val idToken: String) : RegisterIntent()
    object GoogleSignInCancelled : RegisterIntent()
    object GoogleSignInFailed : RegisterIntent()

    // — Navigation / cleanup —
    object NavigateToLoginClicked : RegisterIntent()
    object ErrorDismissed : RegisterIntent()
}
