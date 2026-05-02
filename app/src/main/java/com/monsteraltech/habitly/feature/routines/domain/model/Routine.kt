package com.monsteraltech.habitly.feature.routines.domain.model

enum class RoutineType {
    PERSONAL,
    HOUSEHOLD
}

enum class RoutineFrequency {
    DAILY
}

data class Routine(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: RoutineType = RoutineType.PERSONAL,
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val createdAt: Long = System.currentTimeMillis(),
    val authorId: String = "",
    val lastCompletedAt: Long? = null,
    val lastCompletedBy: String? = null
)
