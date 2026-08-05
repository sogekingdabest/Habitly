package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 7, 8)

    /** Sin tolerancia: reproduce el comportamiento clásico de "días naturales seguidos". */
    private fun strict(dates: List<LocalDate>, on: LocalDate = today) =
        StreakCalculator.calculate(dates, on, graceMisses = 0)

    // ---------- Días naturales (comportamiento clásico, sin tolerancia) ----------

    @Test
    fun `empty history returns all zeros`() {
        val result = strict(emptyList())

        assertEquals(0, result.current)
        assertEquals(0, result.best)
        assertEquals(0, result.total)
    }

    @Test
    fun `three consecutive days ending today`() {
        val result = strict(listOf(today, today.minusDays(1), today.minusDays(2)))

        assertEquals(3, result.current)
        assertEquals(3, result.best)
        assertEquals(3, result.total)
    }

    @Test
    fun `streak ending yesterday still counts as current`() {
        val result = strict(listOf(today.minusDays(1), today.minusDays(2)))

        assertEquals(2, result.current)
    }

    @Test
    fun `broken streak has zero current but keeps best`() {
        val result = strict(listOf(today.minusDays(5), today.minusDays(4), today.minusDays(3)))

        assertEquals(0, result.current)
        assertEquals(3, result.best)
        assertEquals(3, result.total)
    }

    @Test
    fun `best streak is the longest past run`() {
        val dates = listOf(
            today,
            today.minusDays(5), today.minusDays(6), today.minusDays(7), today.minusDays(8)
        )

        val result = strict(dates)

        assertEquals(1, result.current)
        assertEquals(4, result.best)
        assertEquals(5, result.total)
    }

    @Test
    fun `duplicate dates are counted once`() {
        val result = strict(listOf(today, today, today.minusDays(1)))

        assertEquals(2, result.current)
        assertEquals(2, result.total)
    }

    @Test
    fun `today unmarked does not break the streak`() {
        // Ayer y anteayer hechos, hoy todavía sin marcar: la racha sigue viva.
        val result = strict(listOf(today.minusDays(1), today.minusDays(2)))

        assertEquals(2, result.current)
    }

    // ---------- Protector de racha ----------

    @Test
    fun `one forgiven miss keeps the streak alive`() {
        // Hecho hoy y anteayer; ayer se falló. Con tolerancia 1 la racha aguanta.
        val dates = listOf(today, today.minusDays(2), today.minusDays(3))

        val result = StreakCalculator.calculate(dates, today, graceMisses = 1)

        assertEquals(3, result.current)
        assertTrue("debe marcarse como protegida", result.graceUsed)
    }

    @Test
    fun `two misses break the streak even with the protector`() {
        val dates = listOf(today, today.minusDays(3), today.minusDays(4))

        val result = StreakCalculator.calculate(dates, today, graceMisses = 1)

        assertEquals(1, result.current)
    }

    @Test
    fun `graceUsed is false when nothing was forgiven`() {
        val result = StreakCalculator.calculate(
            listOf(today, today.minusDays(1)),
            today,
            graceMisses = 1
        )

        assertEquals(2, result.current)
        assertFalse(result.graceUsed)
    }

    @Test
    fun `best is never smaller than current`() {
        val dates = listOf(today, today.minusDays(2), today.minusDays(3))

        val result = StreakCalculator.calculate(dates, today, graceMisses = 1)

        assertTrue(result.best >= result.current)
    }

    // ---------- Rachas conscientes del calendario ----------

    @Test
    fun `weekly routine builds a streak across weeks`() {
        // Rutina de los lunes. 2026-07-06, 06-29 y 06-22 son lunes consecutivos.
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY)
        )
        val mondays = listOf(
            LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 6, 29),
            LocalDate.of(2026, 6, 22)
        )

        val result = StreakCalculator.forRoutine(routine, mondays, today, graceMisses = 0)

        assertEquals("tres lunes seguidos son racha 3", 3, result.current)
        assertEquals(3, result.best)
    }

    @Test
    fun `days when the routine was not due neither add nor break`() {
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY)
        )
        // Solo dos lunes: los seis días intermedios no cuentan como fallo.
        val mondays = listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 6, 29))

        val result = StreakCalculator.forRoutine(routine, mondays, today, graceMisses = 0)

        assertEquals(2, result.current)
    }

    @Test
    fun `missing a scheduled occurrence breaks a weekly streak without grace`() {
        val routine = Routine(
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY)
        )
        // Se saltó el lunes 2026-06-29.
        val mondays = listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 6, 22))

        val result = StreakCalculator.forRoutine(routine, mondays, today, graceMisses = 0)

        assertEquals(1, result.current)
    }

    @Test
    fun `daily routine through forRoutine matches the classic behaviour`() {
        val routine = Routine(frequency = RoutineFrequency.DAILY)
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))

        val result = StreakCalculator.forRoutine(routine, dates, today, graceMisses = 0)

        assertEquals(3, result.current)
        assertEquals(3, result.best)
    }

    // ---------- Cada N días ----------

    @Test
    fun `interval streak counts completions spaced within the interval`() {
        val result = StreakCalculator.calculateByInterval(
            completedDates = listOf(today, today.minusDays(10), today.minusDays(20)),
            today = today,
            intervalDays = 10,
            graceDays = 0
        )

        assertEquals(3, result.current)
        assertEquals(3, result.best)
    }

    @Test
    fun `interval streak breaks when a gap is too long`() {
        val result = StreakCalculator.calculateByInterval(
            completedDates = listOf(today, today.minusDays(30), today.minusDays(40)),
            today = today,
            intervalDays = 10,
            graceDays = 0
        )

        assertEquals(1, result.current)
    }

    @Test
    fun `interval streak is zero when the last completion is too old`() {
        val result = StreakCalculator.calculateByInterval(
            completedDates = listOf(today.minusDays(30)),
            today = today,
            intervalDays = 10,
            graceDays = 0
        )

        assertEquals(0, result.current)
    }

    @Test
    fun `interval grace tolerates a longer gap`() {
        val result = StreakCalculator.calculateByInterval(
            completedDates = listOf(today, today.minusDays(15)),
            today = today,
            intervalDays = 10,
            graceDays = 10
        )

        assertEquals(2, result.current)
        assertTrue(result.graceUsed)
    }

    @Test
    fun `forRoutine dispatches interval routines to the interval rule`() {
        val routine = Routine(frequency = RoutineFrequency.EVERY_N_DAYS, intervalDays = 10)
        val dates = listOf(today, today.minusDays(10), today.minusDays(20))

        val result = StreakCalculator.forRoutine(routine, dates, today, graceMisses = 0)

        assertEquals(3, result.current)
        assertEquals(3, result.total)
    }

    // ---------- Ventana de vida y frecuencia mensual ----------

    private fun millisOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `days after the end date do not break the streak`() {
        // Rutina diaria que terminó hace 3 días; completada sus últimos 3 días seguidos.
        // Sin la ventana, los días posteriores al fin contarían como fallos y romperían la racha.
        val routine = Routine(
            frequency = RoutineFrequency.DAILY,
            endDate = millisOf(today.minusDays(3))
        )
        val dates = listOf(today.minusDays(5), today.minusDays(4), today.minusDays(3))

        val result = StreakCalculator.forRoutine(routine, dates, today, graceMisses = 0)

        assertEquals("tras la fecha de fin no hay fallos que rompan la racha", 3, result.current)
    }

    @Test
    fun `monthly routine builds a streak across months`() {
        // hoy = 2026-07-08 (día 8); ancla el día 8 vía fecha de inicio.
        val routine = Routine(
            frequency = RoutineFrequency.MONTHLY,
            startDate = millisOf(LocalDate.of(2026, 5, 8))
        )
        val days = listOf(
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 5, 8)
        )

        val result = StreakCalculator.forRoutine(routine, days, today, graceMisses = 0)

        assertEquals("tres meses seguidos en el día ancla son racha 3", 3, result.current)
        assertEquals(3, result.best)
    }
}
