package com.monsteraltech.habitly.feature.aiassistant.domain.model

import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency

/**
 * A routine proposed by the AI assistant, extracted from the structured block the model appends to
 * its answers.
 *
 * [scheduledDays] uses the `java.util.Calendar` constants (Sunday = 1), like
 * [com.monsteraltech.habitly.feature.routines.domain.model.Routine].
 */
data class AiRoutineSuggestion(
    val title: String,
    val description: String = "",
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val scheduledDays: List<Int> = emptyList(),
    val intervalDays: Int? = null
)
