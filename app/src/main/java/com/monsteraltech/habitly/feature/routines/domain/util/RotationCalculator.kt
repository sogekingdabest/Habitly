package com.monsteraltech.habitly.feature.routines.domain.util

/**
 * Decides whose turn is next on a rotating routine.
 *
 * A pure function so it is testable. The order is that of the household's `members` list, which
 * Firestore returns stably, so the turn is predictable for everyone.
 */
object RotationCalculator {

    /**
     * The member after [current], wrapping around at the end.
     *
     * If nobody held the turn, or whoever did is no longer in the household (removed or left), it
     * starts from the first: better than leaving the routine ownerless.
     */
    fun next(members: List<String>, current: String?): String? {
        if (members.isEmpty()) return null
        val index = members.indexOf(current)
        if (index == -1) return members.first()
        return members[(index + 1) % members.size]
    }
}
