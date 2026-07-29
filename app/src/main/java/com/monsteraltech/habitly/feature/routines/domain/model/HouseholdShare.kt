package com.monsteraltech.habitly.feature.routines.domain.model

/**
 * How the household is sharing out its shared routines.
 *
 * It deliberately **carries no order or positions**: the counts are keyed by uid and whoever paints
 * them orders them like the household members. A ranking in a shared-living app points at whoever
 * does least, and that genuinely hurts among people who live together.
 */
data class HouseholdShareSummary(
    /** Household routines completed per member (uid → how many) in the current week. */
    val thisWeek: Map<String, Int> = emptyMap(),
    /** The same for the previous week, only to give context. */
    val lastWeek: Map<String, Int> = emptyMap(),
    /** Days running on which everything the household had due got done. */
    val houseStreakDays: Int = 0,
    /** Whether the household has any shared routine (without them there is nothing to show). */
    val hasHouseholdRoutines: Boolean = false
) {
    val thisWeekTotal: Int get() = thisWeek.values.sum()

    val lastWeekTotal: Int get() = lastWeek.values.sum()

    /** Reference for the length of the bars. */
    val maxThisWeek: Int get() = thisWeek.values.maxOrNull() ?: 0
}
