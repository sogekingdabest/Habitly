package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class RoutineScheduleTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 2026-07-13 lunes, 2026-07-15 miércoles, 2026-07-19 domingo. */
    private val monday = LocalDate.of(2026, 7, 13)
    private val wednesday = LocalDate.of(2026, 7, 15)
    private val sunday = LocalDate.of(2026, 7, 19)

    private fun epochOf(date: LocalDate): Long =
        date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    // ---------- Calendario semanal ----------

    @Test
    fun `daily routine is due every day`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY)

        assertTrue(RoutineSchedule.isDueOn(routine, monday, zone))
        assertTrue(RoutineSchedule.isDueOn(routine, wednesday, zone))
        assertTrue(RoutineSchedule.isDueOn(routine, sunday, zone))
    }

    @Test
    fun `weekly routine is due only on its days`() {
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
        )

        assertTrue(RoutineSchedule.isDueOn(routine, monday, zone))
        assertTrue(RoutineSchedule.isDueOn(routine, wednesday, zone))
        assertFalse(RoutineSchedule.isDueOn(routine, sunday, zone))
    }

    @Test
    fun `custom routine without days is due every day`() {
        val routine = Routine(frequency = RoutineFrequency.CUSTOM, scheduledDays = emptyList())

        assertTrue(RoutineSchedule.isDueOn(routine, monday, zone))
        assertTrue(RoutineSchedule.isDueOn(routine, sunday, zone))
    }

    @Test
    fun `custom routine with days behaves like weekly`() {
        val routine = Routine(
            frequency = RoutineFrequency.CUSTOM,
            scheduledDays = listOf(Calendar.TUESDAY, Calendar.THURSDAY)
        )

        assertFalse(RoutineSchedule.isDueOn(routine, monday, zone))
        assertFalse(RoutineSchedule.isDueOn(routine, wednesday, zone))
    }

    @Test
    fun `sunday maps correctly between java time and Calendar`() {
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.SUNDAY)
        )

        assertTrue(RoutineSchedule.isDueOn(routine, sunday, zone))
        assertFalse(RoutineSchedule.isDueOn(routine, monday, zone))
    }

    // ---------- Cada N días ----------

    @Test
    fun `interval routine never completed is due`() {
        val routine = Routine(
            frequency = RoutineFrequency.EVERY_N_DAYS,
            intervalDays = 10,
            lastCompletedAt = null
        )

        assertTrue(RoutineSchedule.isDueOn(routine, monday, zone))
    }

    @Test
    fun `interval routine is not due before the interval elapses`() {
        val routine = Routine(
            frequency = RoutineFrequency.EVERY_N_DAYS,
            intervalDays = 10,
            lastCompletedAt = epochOf(monday)
        )

        assertFalse(RoutineSchedule.isDueOn(routine, monday.plusDays(9), zone))
    }

    @Test
    fun `interval routine is due once the interval elapses`() {
        val routine = Routine(
            frequency = RoutineFrequency.EVERY_N_DAYS,
            intervalDays = 10,
            lastCompletedAt = epochOf(monday)
        )

        assertTrue(RoutineSchedule.isDueOn(routine, monday.plusDays(10), zone))
        assertTrue(RoutineSchedule.isDueOn(routine, monday.plusDays(30), zone))
    }

    @Test
    fun `interval routine ignores the day of the week`() {
        val routine = Routine(
            frequency = RoutineFrequency.EVERY_N_DAYS,
            intervalDays = 1,
            scheduledDays = listOf(Calendar.SUNDAY),
            lastCompletedAt = null
        )

        assertTrue(RoutineSchedule.isDueOn(routine, monday, zone))
    }

    // ---------- Modo vacaciones ----------

    @Test
    fun `paused routine is never due`() {
        val routine = Routine(
            frequency = RoutineFrequency.DAILY,
            pausedUntil = epochOf(monday.plusDays(7))
        )

        assertTrue(RoutineSchedule.isPausedOn(routine, monday, zone))
        assertFalse(RoutineSchedule.isDueOn(routine, monday, zone))
    }

    @Test
    fun `pause includes its last day and ends the day after`() {
        val until = monday.plusDays(3)
        val routine = Routine(frequency = RoutineFrequency.DAILY, pausedUntil = epochOf(until))

        assertTrue("el último día sigue en pausa", RoutineSchedule.isPausedOn(routine, until, zone))
        assertFalse(RoutineSchedule.isPausedOn(routine, until.plusDays(1), zone))
        assertTrue(RoutineSchedule.isDueOn(routine, until.plusDays(1), zone))
    }

    @Test
    fun `routine without pause is not paused`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY, pausedUntil = null)

        assertFalse(RoutineSchedule.isPausedOn(routine, monday, zone))
    }

    // ---------- Completado ----------

    @Test
    fun `routine never completed is not completed today`() {
        assertFalse(RoutineSchedule.isCompletedOn(Routine(lastCompletedAt = null), monday, zone))
    }

    @Test
    fun `routine completed today is completed today`() {
        val routine = Routine(lastCompletedAt = epochOf(monday))

        assertTrue(RoutineSchedule.isCompletedOn(routine, monday, zone))
    }

    @Test
    fun `routine completed yesterday is not completed today`() {
        val routine = Routine(lastCompletedAt = epochOf(monday.minusDays(1)))

        assertFalse(RoutineSchedule.isCompletedOn(routine, monday, zone))
    }

    @Test
    fun `completion at the very start of the day still counts`() {
        val routine = Routine(
            lastCompletedAt = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        )

        assertTrue(RoutineSchedule.isCompletedOn(routine, monday, zone))
    }

    // ---------- Pendiente ----------

    @Test
    fun `due and not completed is pending`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY, lastCompletedAt = null)

        assertTrue(RoutineSchedule.isPendingOn(routine, monday, zone))
    }

    @Test
    fun `due but already completed is not pending`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY, lastCompletedAt = epochOf(monday))

        assertFalse(RoutineSchedule.isPendingOn(routine, monday, zone))
    }

    @Test
    fun `not due today is not pending even if never completed`() {
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY),
            lastCompletedAt = null
        )

        assertFalse(RoutineSchedule.isPendingOn(routine, wednesday, zone))
    }

    @Test
    fun `paused routine is not pending`() {
        val routine = Routine(
            frequency = RoutineFrequency.DAILY,
            lastCompletedAt = null,
            pausedUntil = epochOf(monday.plusDays(2))
        )

        assertFalse(RoutineSchedule.isPendingOn(routine, monday, zone))
    }

    // ---------- Ocurrencias esperadas ----------

    @Test
    fun `daily routine expects one occurrence per day`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY)

        assertEquals(7, RoutineSchedule.expectedOccurrences(routine, monday, monday.plusDays(6)))
    }

    @Test
    fun `weekly routine expects one occurrence per scheduled day`() {
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY, Calendar.FRIDAY)
        )

        // Del lunes 13 al domingo 26: dos lunes y dos viernes.
        assertEquals(4, RoutineSchedule.expectedOccurrences(routine, monday, monday.plusDays(13)))
    }

    @Test
    fun `interval routine expects occurrences based on its interval`() {
        val routine = Routine(frequency = RoutineFrequency.EVERY_N_DAYS, intervalDays = 10)

        // 30 días con intervalo de 10 => 3 ocurrencias.
        assertEquals(3, RoutineSchedule.expectedOccurrences(routine, monday, monday.plusDays(29)))
    }

    @Test
    fun `expected occurrences of an inverted range is zero`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY)

        assertEquals(0, RoutineSchedule.expectedOccurrences(routine, monday, monday.minusDays(1)))
    }
}
