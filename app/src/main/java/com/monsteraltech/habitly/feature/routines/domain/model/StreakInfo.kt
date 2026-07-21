package com.monsteraltech.habitly.feature.routines.domain.model

/**
 * Resumen de cumplimiento de una rutina calculado a partir de su historial de completados.
 *
 * Las rachas se cuentan en **ocurrencias programadas**, no en días naturales: para una rutina
 * de los lunes, dos lunes seguidos son racha 2 aunque entre medias haya seis días sin tocar.
 *
 * @param current ocurrencias consecutivas cumplidas que llegan hasta hoy (0 si la racha se rompió).
 * @param best mejor racha histórica.
 * @param total número total de días completados.
 * @param graceUsed la racha actual sigue viva porque el protector perdonó un fallo.
 */
data class StreakInfo(
    val current: Int = 0,
    val best: Int = 0,
    val total: Int = 0,
    val graceUsed: Boolean = false
)
