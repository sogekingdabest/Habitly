package com.monsteraltech.habitly.feature.widget.domain

/** Session state as far as the widget is concerned. */
enum class WidgetState {
    READY,        // Hay sesión y casa activa: se muestran datos.
    NO_SESSION,   // No hay usuario autenticado.
    NO_HOUSEHOLD  // Hay usuario pero aún no ha creado/entrado en una casa.
}

/**
 * A checkable line of the widget. The [id] is what travels in the callback's `ActionParameters`:
 * without it the widget could only show the list, not tick it off.
 */
data class WidgetLine(val id: String, val label: String)

/**
 * The data the home-screen widget paints, already resolved (flat names) so the widget's composition
 * is instant.
 */
data class WidgetSnapshot(
    val state: WidgetState = WidgetState.NO_SESSION,
    val pendingItems: List<WidgetLine> = emptyList(),
    val pendingRoutines: List<WidgetLine> = emptyList()
)
