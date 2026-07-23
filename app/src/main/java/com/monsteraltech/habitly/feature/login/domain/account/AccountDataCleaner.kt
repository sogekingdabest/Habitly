package com.monsteraltech.habitly.feature.login.domain.account

/**
 * Limpieza de datos locales ligados a la cuenta (bases de datos, preferencias…) al **borrar la
 * cuenta**. Cada feature con estado local sensible aporta su implementación por multibinding de
 * Hilt (`@Binds @IntoSet`) y AuthRepositoryImpl las ejecuta todas: así ningún borrado de cuenta
 * puede olvidarse de una limpieza.
 *
 * OJO: NO se ejecuta en el cierre de sesión normal. Un logout (voluntario o por caducidad de la
 * sesión de Firebase) no debe llevarse el historial: el usuario volvería a entrar y lo encontraría
 * vacío. Al desinstalar la app, Android ya borra todo el almacenamiento interno por su cuenta.
 */
interface AccountDataCleaner {
    suspend fun clearAccountData()
}
