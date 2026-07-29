package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Single source of truth for "is this routine due today?" and "is it already done today?".
 *
 * Pure functions with no Android or Firestore dependency, so they are testable under JUnit and so
 * the routines screen, the dashboard, the widget and the reminder worker all answer exactly the
 * same rather than each duplicating the logic.
 */
object RoutineSchedule {

    /**
     * Is it due on [date]? Accounts for the pause, the weekly calendar and, for interval routines,
     * how long it has been since the last time.
     */
    fun isDueOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (isPausedOn(routine, date, zone)) return false
        return when (routine.frequency) {
            RoutineFrequency.EVERY_N_DAYS -> isIntervalDueOn(routine, date, zone)
            else -> matchesDayOfWeek(routine, date)
        }
    }

    /**
     * The weekly-calendar part only, with no pause or interval. Used by the streak calculation,
     * which needs to know which past days were due without dragging in the routine's current state.
     */
    fun matchesDayOfWeek(routine: Routine, date: LocalDate): Boolean {
        val dayOfWeek = date.toCalendarDayOfWeek()
        return when (routine.frequency) {
            RoutineFrequency.DAILY -> true
            RoutineFrequency.WEEKLY -> routine.scheduledDays.contains(dayOfWeek)
            RoutineFrequency.CUSTOM ->
                routine.scheduledDays.isEmpty() || routine.scheduledDays.contains(dayOfWeek)
            // The day of the week does not constrain interval routines.
            RoutineFrequency.EVERY_N_DAYS -> true
        }
    }

    /** Is it in holiday mode on [date]? */
    fun isPausedOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val pausedUntil = routine.pausedUntil ?: return false
        val until = Instant.ofEpochMilli(pausedUntil).atZone(zone).toLocalDate()
        return !date.isAfter(until)
    }

    /** Was the routine marked completed on [date]? */
    fun isCompletedOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val lastCompletedAt = routine.lastCompletedAt ?: return false
        return Instant.ofEpochMilli(lastCompletedAt).atZone(zone).toLocalDate() == date
    }

    /** Is it due on [date] and still not done? */
    fun isPendingOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = isDueOn(routine, date, zone) && !isCompletedOn(routine, date, zone)

    /**
     * How many times it was due between [from] and [to], both inclusive. This is the denominator of
     * the completion rate.
     *
     * For interval routines it is estimated from the interval itself: a past "was it due that day?"
     * would depend on the completion history of the time, which is not reconstructed.
     */
    fun expectedOccurrences(routine: Routine, from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 0

        val totalDays = ChronoUnit.DAYS.between(from, to) + 1
        if (routine.frequency == RoutineFrequency.EVERY_N_DAYS) {
            val interval = routine.intervalDays?.takeIf { it > 0 } ?: 1
            return ((totalDays + interval - 1) / interval).toInt()
        }

        var count = 0
        var cursor = from
        while (!cursor.isAfter(to)) {
            if (matchesDayOfWeek(routine, cursor)) count++
            cursor = cursor.plusDays(1)
        }
        return count
    }

    /**
     * An interval routine is due when it has never been done, or when at least [Routine.intervalDays]
     * days have passed since the last time.
     */
    private fun isIntervalDueOn(routine: Routine, date: LocalDate, zone: ZoneId): Boolean {
        val interval = routine.intervalDays?.takeIf { it > 0 } ?: return true
        val lastCompletedAt = routine.lastCompletedAt ?: return true
        val lastDate = Instant.ofEpochMilli(lastCompletedAt).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(lastDate, date) >= interval
    }

    /** `java.time` numbers Monday=1..Sunday=7; `Calendar` (what [Routine] stores) Sunday=1..Saturday=7. */
    private fun LocalDate.toCalendarDayOfWeek(): Int = dayOfWeek.value % 7 + 1
}
