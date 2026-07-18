package com.monsteraltech.habitly.feature.routines.domain.model

/**
 * Resumen de cumplimiento de una rutina calculado a partir de su historial de completados.
 *
 * @param current días consecutivos completados que terminan hoy o ayer (0 si la racha se rompió).
 * @param best mejor racha histórica de días consecutivos.
 * @param total número total de días completados.
 */
data class StreakInfo(
    val current: Int = 0,
    val best: Int = 0,
    val total: Int = 0
)
