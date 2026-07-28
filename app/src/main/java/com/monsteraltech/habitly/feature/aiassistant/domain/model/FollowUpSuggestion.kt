package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * Which extraction the follow-up chip triggers after a proposal that produced no card ("Yes, create
 * them", "Yes, to the list"). The target is fixed when the proposal is detected, from *what* the
 * assistant message proposes, and is never re-derived from the text when the chip is tapped — doing
 * that offered "create routines" after a shopping list. The chip's label, prompt and confirmation
 * come from the presentation layer based on this target.
 */
enum class FollowUpTarget {
    ROUTINES, SHOPPING, BOTH;

    val includesRoutines: Boolean get() = this != SHOPPING
    val includesShopping: Boolean get() = this != ROUTINES
}
