package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.StreakInfo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Computes completion streaks from the dates a routine was completed on. Pure functions with no
 * Android or Firestore dependency, so they are testable under JUnit.
 *
 * Two design rules, both meant to keep the streak from being demotivating:
 *
 * 1. **Scheduled occurrences are counted, not calendar days.** A Monday routine builds a streak
 *    Monday after Monday; days it was not due neither add nor break. (This used to count
 *    consecutive days, so no non-daily routine ever got past a streak of 1.)
 * 2. **Streak protector**: up to [DEFAULT_GRACE_MISSES] missed occurrences are forgiven before the
 *    streak is considered broken. Today never counts as a miss until it is over.
 */
object StreakCalculator {

    /** Misses forgiven before the streak breaks. */
    const val DEFAULT_GRACE_MISSES = 1

    /** How many days back the scan goes, so the calculation cannot grow without bound. */
    private const val MAX_LOOKBACK_DAYS = 400L

    /** Entry point: picks the rule matching the routine's frequency. */
    fun forRoutine(
        routine: Routine,
        completedDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
        graceMisses: Int = DEFAULT_GRACE_MISSES
    ): StreakInfo {
        if (routine.frequency == RoutineFrequency.EVERY_N_DAYS) {
            val interval = routine.intervalDays?.takeIf { it > 0 } ?: 1
            return calculateByInterval(
                completedDates = completedDates,
                today = today,
                intervalDays = interval,
                graceDays = graceMisses * interval
            )
        }
        return calculate(
            completedDates = completedDates,
            today = today,
            graceMisses = graceMisses,
            isDueOn = { date -> RoutineSchedule.matchesDayOfWeek(routine, date) }
        )
    }

    /**
     * Calendar streaks: walks the days it was due. With the default [isDueOn] (always due) this is
     * the same as counting calendar days.
     */
    fun calculate(
        completedDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
        graceMisses: Int = DEFAULT_GRACE_MISSES,
        isDueOn: (LocalDate) -> Boolean = { true }
    ): StreakInfo {
        val days = completedDates.toSortedSet()
        if (days.isEmpty()) return StreakInfo()

        val earliest = days.first()
        val horizon = maxOf(earliest, today.minusDays(MAX_LOOKBACK_DAYS))

        // Current streak: backwards from today, forgiving up to graceMisses occurrences.
        var current = 0
        var misses = 0
        var cursor = today
        while (!cursor.isBefore(horizon)) {
            if (isDueOn(cursor)) {
                when {
                    days.contains(cursor) -> current++
                    // Today is not over yet: it cannot count as a miss.
                    cursor == today -> Unit
                    else -> {
                        misses++
                        if (misses > graceMisses) break
                    }
                }
            }
            cursor = cursor.minusDays(1)
        }

        // Best streak: longest run of consecutive completed occurrences, no tolerance.
        var best = 0
        var run = 0
        var scan = horizon
        while (!scan.isAfter(today)) {
            if (isDueOn(scan)) {
                when {
                    days.contains(scan) -> {
                        run++
                        if (run > best) best = run
                    }
                    // Today, still unmarked, does not cut the run.
                    scan == today -> Unit
                    else -> run = 0
                }
            }
            scan = scan.plusDays(1)
        }

        return StreakInfo(
            current = current,
            best = maxOf(best, current),
            total = days.size,
            graceUsed = misses > 0 && current > 0
        )
    }

    /**
     * Interval streaks ("every N days"): the streak stays alive while the gap between completions
     * does not exceed [intervalDays] plus the [graceDays] tolerance.
     */
    fun calculateByInterval(
        completedDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
        intervalDays: Int,
        graceDays: Int = 0
    ): StreakInfo {
        val days = completedDates.toSortedSet()
        if (days.isEmpty()) return StreakInfo()

        val interval = intervalDays.coerceAtLeast(1)
        val allowed = interval + graceDays.coerceAtLeast(0)
        val newestFirst = days.toList().asReversed()

        var current = 0
        var graceUsed = false
        val mostRecent = newestFirst.first()
        if (ChronoUnit.DAYS.between(mostRecent, today) <= allowed) {
            current = 1
            if (ChronoUnit.DAYS.between(mostRecent, today) > interval) graceUsed = true

            var previous = mostRecent
            for (date in newestFirst.drop(1)) {
                val gap = ChronoUnit.DAYS.between(date, previous)
                if (gap > allowed) break
                if (gap > interval) graceUsed = true
                current++
                previous = date
            }
        }

        // Best streak: longest run with gaps within the interval, no tolerance.
        var best = 1
        var run = 1
        var previous: LocalDate? = null
        for (date in days) {
            val prev = previous
            if (prev != null) {
                run = if (ChronoUnit.DAYS.between(prev, date) <= interval) run + 1 else 1
                if (run > best) best = run
            }
            previous = date
        }

        return StreakInfo(
            current = current,
            best = maxOf(best, current),
            total = days.size,
            graceUsed = graceUsed && current > 0
        )
    }
}
