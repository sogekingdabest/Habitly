package com.monsteraltech.habitly.feature.widget.domain

enum class WidgetState {
    READY,
    NO_SESSION,
    NO_HOUSEHOLD
}

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
