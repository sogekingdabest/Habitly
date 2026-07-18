package com.monsteraltech.habitly.feature.login.domain.model

/**
 * Firebase exige un inicio de sesión reciente para operaciones sensibles como
 * borrar la cuenta. Cuando la sesión es demasiado antigua se lanza esta excepción
 * para que la UI pida al usuario volver a iniciar sesión.
 */
class ReauthenticationRequiredException :
    Exception("Es necesario volver a iniciar sesión para completar esta acción")
