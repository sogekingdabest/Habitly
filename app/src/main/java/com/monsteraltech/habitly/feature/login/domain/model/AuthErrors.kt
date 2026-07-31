package com.monsteraltech.habitly.feature.login.domain.model

/**
 * Firebase requires a recent sign-in for sensitive operations such as deleting the account. When
 * the session is too old this exception is thrown so the UI can ask the user to sign in again.
 */
class ReauthenticationRequiredException :
    Exception("Es necesario volver a iniciar sesión para completar esta acción")
