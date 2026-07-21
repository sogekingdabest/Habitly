package com.monsteraltech.habitly.feature.routines.domain.model

import java.time.LocalDate

/**
 * Un día en el que se completó una rutina, y quién lo hizo.
 * Se corresponde con un documento de la subcolección `completions/{yyyy-MM-dd}`.
 */
data class RoutineCompletion(
    val date: LocalDate,
    val userId: String
)
