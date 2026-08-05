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
     * Is it due on [date]? Accounts for the lifetime window, the pause, the calendar (weekly or
     * monthly/yearly anchor) and, for interval routines, how long it has been since the last time.
     */
    fun isDueOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (!isWithinWindow(routine, date, zone)) return false
        if (isPausedOn(routine, date, zone)) return false
        return when (routine.frequency) {
            RoutineFrequency.EVERY_N_DAYS -> isIntervalDueOn(routine, date, zone)
            else -> isScheduledCalendarDay(routine, date, zone)
        }
    }

    /**
     * The calendar part only, with no window, pause or interval. Used by the streak calculation and
     * the heatmap, which need to know which days the routine falls on without dragging in its
     * current state. Covers the weekly cases and the monthly/yearly anchor.
     */
    fun isScheduledCalendarDay(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = when (routine.frequency) {
        RoutineFrequency.MONTHLY ->
            date.dayOfMonth == dueDayOfMonth(anchorDate(routine, zone).dayOfMonth, date)
        RoutineFrequency.YEARLY -> {
            val anchor = anchorDate(routine, zone)
            date.month == anchor.month && date.dayOfMonth == dueDayOfMonth(anchor.dayOfMonth, date)
        }
        else -> matchesDayOfWeek(routine, date)
    }

    /**
     * The weekly-calendar part only. Kept separate because the monthly/yearly cases route through
     * [isScheduledCalendarDay]; this stays as the weekly primitive.
     */
    fun matchesDayOfWeek(routine: Routine, date: LocalDate): Boolean {
        val dayOfWeek = date.toCalendarDayOfWeek()
        return when (routine.frequency) {
            RoutineFrequency.DAILY -> true
            RoutineFrequency.WEEKLY -> routine.scheduledDays.contains(dayOfWeek)
            RoutineFrequency.CUSTOM ->
                routine.scheduledDays.isEmpty() || routine.scheduledDays.contains(dayOfWeek)
            // Neither the day of the week nor a calendar anchor constrain these here.
            RoutineFrequency.EVERY_N_DAYS, RoutineFrequency.MONTHLY, RoutineFrequency.YEARLY -> true
        }
    }

    /**
     * The calendar anchor for monthly/yearly routines: the lifetime start, or the creation date when
     * there is no start. Its day-of-month (monthly) and month+day (yearly) drive when the routine
     * falls due.
     */
    fun anchorDate(routine: Routine, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val anchorMs = routine.startDate ?: routine.createdAt
        return Instant.ofEpochMilli(anchorMs).atZone(zone).toLocalDate()
    }

    /**
     * The day of [date]'s month the routine should fall on for a given [anchorDay], clamped to the
     * month's length so a day-31 (or 29-Feb) anchor still fires on short months' last day.
     */
    private fun dueDayOfMonth(anchorDay: Int, date: LocalDate): Int =
        minOf(anchorDay, date.lengthOfMonth())

    /** Is [date] inside the routine's lifetime window? Both ends are optional and inclusive. */
    fun isWithinWindow(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        routine.startDate?.let {
            if (date.isBefore(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())) return false
        }
        routine.endDate?.let {
            if (date.isAfter(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())) return false
        }
        return true
    }

    /** Has the routine's end date already passed on [date]? (For the "finished" badge.) */
    fun isFinishedOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val end = routine.endDate ?: return false
        return date.isAfter(Instant.ofEpochMilli(end).atZone(zone).toLocalDate())
    }

    /** Has the routine's start date not arrived yet on [date]? (For the "starts on" badge.) */
    fun isNotStartedOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val start = routine.startDate ?: return false
        return date.isBefore(Instant.ofEpochMilli(start).atZone(zone).toLocalDate())
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
     * How many times it was due between [from] and [to], both inclusive, clamped to the routine's
     * lifetime window. This is the denominator of the completion rate, so days before the start or
     * after the end must not inflate it.
     *
     * For interval routines it is estimated from the interval itself: a past "was it due that day?"
     * would depend on the completion history of the time, which is not reconstructed.
     */
    fun expectedOccurrences(
        routine: Routine,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val start = routine.startDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val end = routine.endDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val effFrom = if (start != null && start.isAfter(from)) start else from
        val effTo = if (end != null && end.isBefore(to)) end else to
        if (effTo.isBefore(effFrom)) return 0

        if (routine.frequency == RoutineFrequency.EVERY_N_DAYS) {
            val totalDays = ChronoUnit.DAYS.between(effFrom, effTo) + 1
            val interval = routine.intervalDays?.takeIf { it > 0 } ?: 1
            return ((totalDays + interval - 1) / interval).toInt()
        }

        var count = 0
        var cursor = effFrom
        while (!cursor.isAfter(effTo)) {
            if (isScheduledCalendarDay(routine, cursor, zone)) count++
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
