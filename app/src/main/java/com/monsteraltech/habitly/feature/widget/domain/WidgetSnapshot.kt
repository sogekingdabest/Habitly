package com.monsteraltech.habitly.feature.widget.domain

/** Estado de sesión de cara al widget. */
enum class WidgetState {
    READY,        // Hay sesión y casa activa: se muestran datos.
    NO_SESSION,   // No hay usuario autenticado.
    NO_HOUSEHOLD  // Hay usuario pero aún no ha creado/entrado en una casa.
}

/**
 * Una línea marcable del widget. El [id] es lo que viaja en los `ActionParameters` del
 * callback: sin él el widget solo podría enseñar la lista, no tacharla.
 */
data class WidgetLine(val id: String, val label: String)

/**
 * Datos que pinta el widget de pantalla de inicio, ya resueltos (nombres planos)
 * para que la composición del widget sea instantánea.
 */
data class WidgetSnapshot(
    val state: WidgetState = WidgetState.NO_SESSION,
    val pendingItems: List<WidgetLine> = emptyList(),
    val pendingRoutines: List<WidgetLine> = emptyList()
)
