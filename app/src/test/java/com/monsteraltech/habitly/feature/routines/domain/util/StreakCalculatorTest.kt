package com.monsteraltech.habitly.feature.routines.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 7, 8)

    @Test
    fun `empty history returns all zeros`() {
        val result = StreakCalculator.calculate(emptyList(), today)

        assertEquals(0, result.current)
        assertEquals(0, result.best)
        assertEquals(0, result.total)
    }

    @Test
    fun `three consecutive days ending today`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(3, result.current)
        assertEquals(3, result.best)
        assertEquals(3, result.total)
    }

    @Test
    fun `streak ending yesterday still counts as current`() {
        val dates = listOf(today.minusDays(1), today.minusDays(2))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(2, result.current)
    }

    @Test
    fun `broken streak has zero current but keeps best`() {
        // Última racha terminó hace 3 días.
        val dates = listOf(today.minusDays(5), today.minusDays(4), today.minusDays(3))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(0, result.current)
        assertEquals(3, result.best)
        assertEquals(3, result.total)
    }

    @Test
    fun `best streak is the longest past run`() {
        val dates = listOf(
            today, // racha actual de 1
            today.minusDays(5), today.minusDays(6), today.minusDays(7), today.minusDays(8) // racha de 4
        )

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(1, result.current)
        assertEquals(4, result.best)
        assertEquals(5, result.total)
    }

    @Test
    fun `duplicate dates are counted once`() {
        val dates = listOf(today, today, today.minusDays(1))

        val result = StreakCalculator.calculate(dates, today)

        assertEquals(2, result.current)
        assertEquals(2, result.total)
    }
}
