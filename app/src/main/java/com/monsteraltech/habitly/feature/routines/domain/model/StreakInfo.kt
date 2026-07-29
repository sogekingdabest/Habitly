package com.monsteraltech.habitly.feature.routines.domain.model

/**
 * A routine's completion summary, computed from its completion history.
 *
 * Streaks are counted in **scheduled occurrences**, not calendar days: for a Monday routine, two
 * Mondays running are a streak of 2 even with six not-due days in between.
 *
 * @param current consecutive completed occurrences reaching up to today (0 if the streak broke).
 * @param best best streak ever.
 * @param total total number of completed days.
 * @param graceUsed the current streak is still alive because the protector forgave a miss.
 */
data class StreakInfo(
    val current: Int = 0,
    val best: Int = 0,
    val total: Int = 0,
    val graceUsed: Boolean = false
)
