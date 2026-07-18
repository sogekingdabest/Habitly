package com.monsteraltech.habitly.feature.widget.domain

/** Estado de sesión de cara al widget. */
enum class WidgetState {
    READY,        // Hay sesión y casa activa: se muestran datos.
    NO_SESSION,   // No hay usuario autenticado.
    NO_HOUSEHOLD  // Hay usuario pero aún no ha creado/entrado en una casa.
}

/**
 * Datos que pinta el widget de pantalla de inicio, ya resueltos (nombres planos)
 * para que la composición del widget sea instantánea.
 */
data class WidgetSnapshot(
    val state: WidgetState = WidgetState.NO_SESSION,
    val pendingItems: List<String> = emptyList(),
    val pendingRoutines: List<String> = emptyList()
)
