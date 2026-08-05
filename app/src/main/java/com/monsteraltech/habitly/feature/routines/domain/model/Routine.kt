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
    EVERY_N_DAYS("Cada N días"),
    /** Once a month, on the day of month of the anchor date (start date, or creation). */
    MONTHLY("Mensual"),
    /** Once a year, on the month+day of the anchor date (start date, or creation). */
    YEARLY("Anual")
}

/**
 * How loudly a routine's reminder arrives. Each value maps to its own notification channel, because
 * Android freezes a channel's sound and vibration the moment it is created; the user then tunes each
 * level to taste from the system settings.
 *
 * Same warning as [RoutineFrequency]: Firestore serialises this by name, so adding a value here
 * breaks older app versions reading a routine that uses it.
 */
enum class NotificationLevel {
    /** Arrives without sound or vibration. */
    SILENT,
    DEFAULT,
    /** Pops up on screen. */
    HIGH
}

data class Routine(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    /**
     * Emoji shown next to the title and in the reminder, so the routine is recognisable without
     * reading it. Empty means no icon. A plain string on purpose: it syncs with the household for
     * free and there are no image files to keep, upload or clean up.
     */
    val icon: String = "",
    /** Which channel the reminder uses. */
    val notificationLevel: NotificationLevel = NotificationLevel.DEFAULT,
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
    /**
     * The routine's lifetime window (epoch ms). Before [startDate] or after [endDate] it is neither
     * due nor notified, and days outside the window do not break the streak. Null means open-ended
     * on that side. For [RoutineFrequency.MONTHLY]/[RoutineFrequency.YEARLY], [startDate] (or
     * [createdAt] when null) also acts as the calendar anchor.
     */
    val startDate: Long? = null,
    val endDate: Long? = null,
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
