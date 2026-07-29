package com.monsteraltech.habitly.feature.routines.domain.model

enum class RoutineType {
    PERSONAL,
    HOUSEHOLD
}

/**
 * Careful: Firestore serialises this enum by name. Adding a new value makes an old app version
 * crash when it reads a routine that uses it, so all the devices in a household should update
 * together.
 */
enum class RoutineFrequency(val label: String) {
    DAILY("Diaria"),
    WEEKLY("Semanal"),
    CUSTOM("Personalizada"),
    EVERY_N_DAYS("Cada N días")
}

data class Routine(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: RoutineType = RoutineType.PERSONAL,
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val scheduledDays: List<Int> = emptyList(),
    /** How many days apart it is due, only for [RoutineFrequency.EVERY_N_DAYS]. */
    val intervalDays: Int? = null,
    /**
     * Holiday mode: until this date passes, the routine is neither due nor notified and the skipped
     * days do not break the streak. Null means no pause.
     */
    val pausedUntil: Long? = null,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val authorId: String = "",
    val lastCompletedAt: Long? = null,
    val lastCompletedBy: String? = null,
    val reminderTime: Int? = null,
    /** The member whose turn it is now. Only meaningful for household routines. */
    val assignedTo: String? = null,
    /** On completion, the turn passes automatically to the next household member. */
    val rotationEnabled: Boolean = false,
    /** Current streak, in scheduled occurrences (not calendar days). Denormalised. */
    val currentStreak: Int = 0,
    /** Best streak ever. Denormalised. */
    val bestStreak: Int = 0,
    /** The current streak is alive thanks to the protector (a miss was forgiven). */
    val streakGraceUsed: Boolean = false
)
