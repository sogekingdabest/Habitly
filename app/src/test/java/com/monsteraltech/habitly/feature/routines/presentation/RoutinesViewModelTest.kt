package com.monsteraltech.habitly.feature.routines.presentation

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RoutinesViewModelTest {

    @Test
    fun `isRoutineCompletedToday returns true for routine completed today`() {
        val today = System.currentTimeMillis()
        val routine = Routine(id = "r1", title = "Test", lastCompletedAt = today)

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine).not())
    }

    @Test
    fun `isRoutineCompletedToday returns false for routine completed yesterday`() {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val routine = Routine(id = "r1", title = "Test", lastCompletedAt = yesterday)

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine))
    }

    @Test
    fun `isRoutineCompletedToday returns false for routine never completed`() {
        val routine = Routine(id = "r1", title = "Test", lastCompletedAt = null)

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine))
    }

    @Test
    fun `isRoutineCompletedToday returns true for routine completed at start of today`() {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val routine = Routine(id = "r1", title = "Test", lastCompletedAt = today.timeInMillis)

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine).not())
    }

    @Test
    fun `isRoutineCompletedToday returns false for routine completed at end of yesterday`() {
        val yesterday = Calendar.getInstance()
        yesterday.set(Calendar.HOUR_OF_DAY, 23)
        yesterday.set(Calendar.MINUTE, 59)
        yesterday.set(Calendar.SECOND, 59)
        yesterday.add(Calendar.DAY_OF_YEAR, -1)

        val routine = Routine(id = "r1", title = "Test", lastCompletedAt = yesterday.timeInMillis)

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine))
    }

    @Test
    fun `isRoutineCompletedToday returns false for weekly routine not scheduled today`() {
        val today = Calendar.getInstance()
        val todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK)
        val otherDay = if (todayDayOfWeek == Calendar.SUNDAY) Calendar.MONDAY else todayDayOfWeek + 1

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val routine = Routine(
            id = "r1",
            title = "Test",
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(otherDay),
            lastCompletedAt = yesterday
        )

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine))
    }

    @Test
    fun `isRoutineCompletedToday returns false for daily routine completed yesterday`() {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val routine = Routine(
            id = "r1",
            title = "Test",
            frequency = RoutineFrequency.DAILY,
            lastCompletedAt = yesterday
        )

        assertFalse(RoutinesViewModel.isRoutineCompletedToday(routine))
    }

    @Test
    fun `isScheduledForDayOfWeek returns true for daily routine`() {
        val routine = Routine(id = "r1", title = "Test", frequency = RoutineFrequency.DAILY)

        assertTrue(routine.isScheduledForDayOfWeek(Calendar.MONDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.FRIDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.SUNDAY))
    }

    @Test
    fun `isScheduledForDayOfWeek returns true only for selected days in weekly routine`() {
        val routine = Routine(
            id = "r1",
            title = "Test",
            frequency = RoutineFrequency.WEEKLY,
            scheduledDays = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
        )

        assertTrue(routine.isScheduledForDayOfWeek(Calendar.MONDAY))
        assertFalse(routine.isScheduledForDayOfWeek(Calendar.TUESDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.WEDNESDAY))
        assertFalse(routine.isScheduledForDayOfWeek(Calendar.THURSDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.FRIDAY))
        assertFalse(routine.isScheduledForDayOfWeek(Calendar.SATURDAY))
        assertFalse(routine.isScheduledForDayOfWeek(Calendar.SUNDAY))
    }

    @Test
    fun `isScheduledForDayOfWeek returns true for custom routine with no days set`() {
        val routine = Routine(
            id = "r1",
            title = "Test",
            frequency = RoutineFrequency.CUSTOM,
            scheduledDays = emptyList()
        )

        assertTrue(routine.isScheduledForDayOfWeek(Calendar.MONDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.SUNDAY))
    }

    @Test
    fun `isScheduledForDayOfWeek returns true only for selected days in custom routine`() {
        val routine = Routine(
            id = "r1",
            title = "Test",
            frequency = RoutineFrequency.CUSTOM,
            scheduledDays = listOf(Calendar.TUESDAY, Calendar.THURSDAY)
        )

        assertFalse(routine.isScheduledForDayOfWeek(Calendar.MONDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.TUESDAY))
        assertFalse(routine.isScheduledForDayOfWeek(Calendar.WEDNESDAY))
        assertTrue(routine.isScheduledForDayOfWeek(Calendar.THURSDAY))
        assertFalse(routine.isScheduledForDayOfWeek(Calendar.FRIDAY))
    }
}
