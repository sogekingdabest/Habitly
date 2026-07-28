package com.monsteraltech.habitly.navigation

/**
 * Pantalla que se pide desde **fuera** de la app: la notificación de recordatorio o un atajo del
 * icono del launcher.
 *
 * Existía ya como un `Boolean` (`navigateToRoutines`) que viajaba de `MainActivity` a
 * `MainScreen`; con dos atajos más, un booleano por destino no escala. El mecanismo es el mismo
 * —`MainActivity` lo apunta al recibir el intent, `MainContent` navega y avisa de que lo ha
 * consumido— pero con un solo canal.
 */
enum class ExternalDestination {
    /** Pestaña de rutinas: notificación de recordatorio y atajo "Rutinas de hoy". */
    ROUTINES,

    /** Lista de la compra con la hoja de alta rápida abierta: atajo "Añadir a la compra". */
    SHOPPING_QUICK_ADD;

    companion object {
        /** Acción del atajo estático de rutinas (`res/xml/shortcuts.xml`). */
        const val ACTION_VIEW_ROUTINES = "com.monsteraltech.habitly.action.VIEW_ROUTINES"

        /** Acción del atajo estático de alta rápida (`res/xml/shortcuts.xml`). */
        const val ACTION_ADD_SHOPPING = "com.monsteraltech.habitly.action.ADD_SHOPPING"

        /** Destino que corresponde a la acción de un atajo, o null si no es de un atajo. */
        fun fromAction(action: String?): ExternalDestination? = when (action) {
            ACTION_VIEW_ROUTINES -> ROUTINES
            ACTION_ADD_SHOPPING -> SHOPPING_QUICK_ADD
            else -> null
        }
    }
}
