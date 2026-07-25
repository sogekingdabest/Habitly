package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class GetHouseholdShareUseCaseTest {

    private val repository = FakeRoutinesRepository()
    private val useCase = GetHouseholdShareUseCase(repository)

    /** Miércoles: la semana en curso empieza el lunes 20 y la anterior el lunes 13. */
    private val today = LocalDate.of(2026, 7, 22)
    private val monday = LocalDate.of(2026, 7, 20)
    private val lastMonday = LocalDate.of(2026, 7, 13)

    private val dani = "uid-dani"
    private val lucia = "uid-lucia"

    @Before
    fun setUp() {
        repository.reset()
    }

    private fun dailyRoutine(id: String) = Routine(
        id = id,
        title = id,
        type = RoutineType.HOUSEHOLD,
        frequency = RoutineFrequency.DAILY
    )

    @Test
    fun `una casa sin rutinas compartidas no tiene reparto que ensenar`() = runTest {
        val summary = useCase(dani, "casa", today).getOrThrow()

        assertFalse(summary.hasHouseholdRoutines)
        assertTrue(summary.thisWeek.isEmpty())
        assertEquals(0, summary.houseStreakDays)
    }

    @Test
    fun `cuenta lo hecho por cada miembro esta semana y la pasada`() = runTest {
        repository.addHouseholdRoutine(dailyRoutine("basura"))
        repository.stubCompletions = mapOf(
            "basura" to listOf(
                RoutineCompletion(monday, dani),
                RoutineCompletion(monday.plusDays(1), lucia),
                RoutineCompletion(today, dani),
                // Semana pasada, solo contexto.
                RoutineCompletion(lastMonday.plusDays(2), lucia)
            )
        )

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(mapOf(dani to 2, lucia to 1), summary.thisWeek)
        assertEquals(mapOf(lucia to 1), summary.lastWeek)
        assertEquals(3, summary.thisWeekTotal)
        assertEquals(1, summary.lastWeekTotal)
        assertEquals(2, summary.maxThisWeek)
    }

    @Test
    fun `la racha de la casa cuenta los dias con todo hecho`() = runTest {
        repository.addHouseholdRoutine(dailyRoutine("basura"))
        repository.addHouseholdRoutine(dailyRoutine("fregar"))
        val days = listOf(today, today.minusDays(1), today.minusDays(2))
        repository.stubCompletions = mapOf(
            "basura" to days.map { RoutineCompletion(it, dani) },
            "fregar" to days.map { RoutineCompletion(it, lucia) }
        )

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(3, summary.houseStreakDays)
    }

    @Test
    fun `un dia con algo sin hacer corta la racha`() = runTest {
        repository.addHouseholdRoutine(dailyRoutine("basura"))
        repository.addHouseholdRoutine(dailyRoutine("fregar"))
        repository.stubCompletions = mapOf(
            "basura" to listOf(
                RoutineCompletion(today, dani),
                RoutineCompletion(today.minusDays(1), dani)
            ),
            // "fregar" no se hizo ayer.
            "fregar" to listOf(RoutineCompletion(today, lucia))
        )

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(1, summary.houseStreakDays)
    }

    @Test
    fun `hoy sin marcar todavia no rompe la racha`() = runTest {
        repository.addHouseholdRoutine(dailyRoutine("basura"))
        repository.stubCompletions = mapOf(
            "basura" to listOf(
                RoutineCompletion(today.minusDays(1), dani),
                RoutineCompletion(today.minusDays(2), dani)
            )
        )

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(2, summary.houseStreakDays)
    }

    @Test
    fun `un dia en el que no tocaba nada no suma ni rompe`() = runTest {
        // Solo los lunes (Calendar.MONDAY), así que martes y miércoles no cuentan.
        repository.addHouseholdRoutine(
            Routine(
                id = "sabanas",
                title = "Cambiar sábanas",
                type = RoutineType.HOUSEHOLD,
                frequency = RoutineFrequency.WEEKLY,
                scheduledDays = listOf(Calendar.MONDAY)
            )
        )
        repository.stubCompletions = mapOf(
            "sabanas" to listOf(
                RoutineCompletion(monday, dani),
                RoutineCompletion(lastMonday, lucia)
            )
        )

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(2, summary.houseStreakDays)
    }

    @Test
    fun `las rutinas por intervalo quedan fuera de la racha pero cuentan en el reparto`() = runTest {
        repository.addHouseholdRoutine(
            Routine(
                id = "filtros",
                title = "Cambiar filtros",
                type = RoutineType.HOUSEHOLD,
                frequency = RoutineFrequency.EVERY_N_DAYS,
                intervalDays = 15
            )
        )
        repository.stubCompletions = mapOf("filtros" to listOf(RoutineCompletion(monday, dani)))

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(0, summary.houseStreakDays)
        assertEquals(mapOf(dani to 1), summary.thisWeek)
    }

    @Test
    fun `una sola consulta de historial por rutina de casa`() = runTest {
        repository.addHouseholdRoutine(dailyRoutine("basura"))
        repository.addHouseholdRoutine(dailyRoutine("fregar"))
        repository.addHouseholdRoutine(dailyRoutine("compra"))

        useCase(dani, "casa", today).getOrThrow()

        assertEquals(3, repository.getCompletionsCalls)
    }

    @Test
    fun `sin casa activa no se consulta nada`() = runTest {
        val summary = useCase(dani, "", today).getOrThrow()

        assertFalse(summary.hasHouseholdRoutines)
        assertEquals(0, repository.getCompletionsCalls)
    }

    @Test
    fun `los completados sin autor no se cuentan`() = runTest {
        repository.addHouseholdRoutine(dailyRoutine("basura"))
        repository.stubCompletions = mapOf(
            "basura" to listOf(
                RoutineCompletion(monday, ""),
                RoutineCompletion(today, dani)
            )
        )

        val summary = useCase(dani, "casa", today).getOrThrow()

        assertEquals(mapOf(dani to 1), summary.thisWeek)
    }
}
