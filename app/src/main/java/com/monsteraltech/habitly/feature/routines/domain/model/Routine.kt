package com.monsteraltech.habitly.feature.routines.domain.model

enum class RoutineType {
    PERSONAL,
    HOUSEHOLD
}

enum class RoutineFrequency(val label: String) {
    DAILY("Diaria"),
    WEEKLY("Semanal"),
    CUSTOM("Personalizada")
}

data class Routine(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: RoutineType = RoutineType.PERSONAL,
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val scheduledDays: List<Int> = emptyList(),
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val authorId: String = "",
    val lastCompletedAt: Long? = null,
    val lastCompletedBy: String? = null,
    val reminderTime: Int? = null
) {
    fun isScheduledForDayOfWeek(dayOfWeek: Int): Boolean {
        return when (frequency) {
            RoutineFrequency.DAILY -> true
            RoutineFrequency.WEEKLY -> scheduledDays.contains(dayOfWeek)
            RoutineFrequency.CUSTOM -> scheduledDays.isEmpty() || scheduledDays.contains(dayOfWeek)
        }
    }
}
