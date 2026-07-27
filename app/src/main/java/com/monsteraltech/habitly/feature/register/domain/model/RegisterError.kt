package com.monsteraltech.habitly.feature.register.domain.model

sealed class RegisterError : Exception() {

    object EmailBlank : RegisterError()
    object EmailInvalidFormat : RegisterError()
    object PasswordTooShort : RegisterError()
    object PasswordNoUppercase : RegisterError()
    object PasswordNoDigit : RegisterError()
    object PasswordsDoNotMatch : RegisterError()
    object DisplayNameBlank : RegisterError()
    object DisplayNameTooShort : RegisterError()

    object EmailAlreadyInUse : RegisterError()
    object NetworkError : RegisterError()
    object GoogleSignInCancelled : RegisterError()
    object GoogleSignInFailed : RegisterError()
    object EmailNotVerifiedYet : RegisterError()

    data class Unknown(override val cause: Throwable) : RegisterError()
}
