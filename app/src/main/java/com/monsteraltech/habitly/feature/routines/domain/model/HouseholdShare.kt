package com.monsteraltech.habitly.feature.routines.domain.model

/**
 * Cómo se está repartiendo la casa las rutinas compartidas.
 *
 * A propósito **no trae orden ni posiciones**: los recuentos van indexados por uid y quien lo
 * pinta los ordena como los miembros de la casa. Un ranking en una app de convivencia señala al
 * que menos hace, y eso hace daño de verdad entre gente que vive junta.
 */
data class HouseholdShareSummary(
    /** Rutinas de casa completadas por miembro (uid → cuántas) en la semana en curso. */
    val thisWeek: Map<String, Int> = emptyMap(),
    /** Lo mismo de la semana anterior, solo para dar contexto. */
    val lastWeek: Map<String, Int> = emptyMap(),
    /** Días seguidos en los que se completó todo lo que tocaba en casa. */
    val houseStreakDays: Int = 0,
    /** Si la casa tiene alguna rutina compartida (sin ellas no hay nada que enseñar). */
    val hasHouseholdRoutines: Boolean = false
) {
    val thisWeekTotal: Int get() = thisWeek.values.sum()

    val lastWeekTotal: Int get() = lastWeek.values.sum()

    /** Referencia para el largo de las barras. */
    val maxThisWeek: Int get() = thisWeek.values.maxOrNull() ?: 0
}
