package com.monsteraltech.habitly.feature.register.presentation.register

sealed class RegisterIntent {

    // — Cambios de campo —
    data class DisplayNameChanged(val value: String) : RegisterIntent()
    data class EmailChanged(val value: String) : RegisterIntent()
    data class PasswordChanged(val value: String) : RegisterIntent()
    data class ConfirmPasswordChanged(val value: String) : RegisterIntent()

    // — Visibilidad de contraseñas —
    object TogglePasswordVisibility : RegisterIntent()
    object ToggleConfirmPasswordVisibility : RegisterIntent()

    // — Acciones principales —
    object RegisterWithEmailClicked : RegisterIntent()
    object SignInWithGoogleClicked : RegisterIntent()

    // — Resultado del Credential Manager (disparado desde la UI) —
    data class GoogleIdTokenReceived(val idToken: String) : RegisterIntent()
    object GoogleSignInCancelled : RegisterIntent()
    object GoogleSignInFailed : RegisterIntent()

    // — Navegación / limpieza —
    object NavigateToLoginClicked : RegisterIntent()
    object ErrorDismissed : RegisterIntent()
}
