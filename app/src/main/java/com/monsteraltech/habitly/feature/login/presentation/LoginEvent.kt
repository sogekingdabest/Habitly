package com.monsteraltech.habitly.feature.login.presentation

sealed class LoginEvent {
    data class EmailChanged(val email: String) : LoginEvent()
    data class PasswordChanged(val password: String) : LoginEvent()
    object LoginClicked : LoginEvent()
    data class GoogleAuthTokenReceived(val idToken: String) : LoginEvent()
    data class GoogleLoginError(val error: String) : LoginEvent()
}
