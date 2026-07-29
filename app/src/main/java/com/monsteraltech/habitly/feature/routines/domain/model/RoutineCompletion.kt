package com.monsteraltech.habitly.feature.routines.domain.model

import java.time.LocalDate

/**
 * A day a routine was completed on, and who did it. Corresponds to a document in the
 * `completions/{yyyy-MM-dd}` subcollection.
 */
data class RoutineCompletion(
    val date: LocalDate,
    val userId: String
)
