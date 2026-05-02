package com.monsteraltech.habitly.feature.register.domain.model

sealed class RegisterError : Exception() {

    // — Errores de validación de entrada (dominio puro) —
    object EmailBlank : RegisterError()
    object EmailInvalidFormat : RegisterError()
    object PasswordTooShort : RegisterError()       // < 8 chars
    object PasswordNoUppercase : RegisterError()    // sin mayúscula
    object PasswordNoDigit : RegisterError()        // sin número
    object PasswordsDoNotMatch : RegisterError()
    object DisplayNameBlank : RegisterError()
    object DisplayNameTooShort : RegisterError()    // < 2 chars

    // — Errores de Firebase / red —
    object EmailAlreadyInUse : RegisterError()
    object NetworkError : RegisterError()
    object GoogleSignInCancelled : RegisterError()
    object GoogleSignInFailed : RegisterError()
    object EmailNotVerifiedYet : RegisterError()    // usado en polling

    // — Fallback genérico —
    data class Unknown(override val cause: Throwable) : RegisterError()
}
