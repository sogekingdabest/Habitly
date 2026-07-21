package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Rotación de turnos y balance semanal de las rutinas de casa. */
class HouseholdRotationTest {

    private val fakeRepository = FakeRoutinesRepository()

    private lateinit var advanceRotation: AdvanceRotationUseCase
    private lateinit var returnRotation: ReturnRotationUseCase
    private lateinit var getBalance: GetHouseholdBalanceUseCase

    private val userId = "ana"
    private val householdId = "house1"
    private val members = listOf("ana", "beto", "carla")

    private val monday = LocalDate.of(2026, 7, 13)

    @Before
    fun setUp() {
        advanceRotation = AdvanceRotationUseCase(fakeRepository)
        returnRotation = ReturnRotationUseCase(fakeRepository)
        getBalance = GetHouseholdBalanceUseCase(fakeRepository)
    }

    @After
    fun tearDown() {
        fakeRepository.reset()
    }

    private fun rotatingRoutine(assignedTo: String? = "ana") = Routine(
        id = "r1",
        title = "Sacar basura",
        type = RoutineType.HOUSEHOLD,
        rotationEnabled = true,
        assignedTo = assignedTo
    )

    // ---------- Avanzar el turno ----------

    @Test
    fun `completing a rotating routine passes the turn on`() = runBlocking {
        val routine = rotatingRoutine(assignedTo = "ana")
        fakeRepository.addHouseholdRoutine(routine)

        val result = advanceRotation(userId, householdId, routine, members)

        assertEquals("beto", result.getOrNull())
        assertEquals("beto", fakeRepository.assignments["r1"])
    }

    @Test
    fun `a routine without rotation is left alone`() = runBlocking {
        val routine = rotatingRoutine(assignedTo = "ana").copy(rotationEnabled = false)
        fakeRepository.addHouseholdRoutine(routine)

        advanceRotation(userId, householdId, routine, members)

        assertTrue(fakeRepository.assignments.isEmpty())
    }

    @Test
    fun `a personal routine never rotates`() = runBlocking {
        val routine = Routine(
            id = "r1",
            title = "Gimnasio",
            type = RoutineType.PERSONAL,
            rotationEnabled = true
        )
        fakeRepository.addPersonalRoutine(routine)

        advanceRotation(userId, householdId, routine, members)

        assertTrue(fakeRepository.assignments.isEmpty())
    }

    @Test
    fun `without members the turn does not change`() = runBlocking {
        val routine = rotatingRoutine(assignedTo = "ana")
        fakeRepository.addHouseholdRoutine(routine)

        advanceRotation(userId, householdId, routine, emptyList())

        assertTrue(fakeRepository.assignments.isEmpty())
    }

    // ---------- Deshacer ----------

    @Test
    fun `undoing a completion gives the turn back to whoever unchecked it`() = runBlocking {
        val routine = rotatingRoutine(assignedTo = "beto")
        fakeRepository.addHouseholdRoutine(routine)

        returnRotation("carla", householdId, routine)

        assertEquals("carla", fakeRepository.assignments["r1"])
    }

    @Test
    fun `undoing a non rotating routine changes nothing`() = runBlocking {
        val routine = rotatingRoutine().copy(rotationEnabled = false)
        fakeRepository.addHouseholdRoutine(routine)

        returnRotation("carla", householdId, routine)

        assertTrue(fakeRepository.assignments.isEmpty())
    }

    // ---------- Balance ----------

    @Test
    fun `without household routines the balance is empty`() = runBlocking {
        val result = getBalance(userId, householdId, monday, monday.plusDays(6))

        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `counts completions per member`() = runBlocking {
        fakeRepository.addHouseholdRoutine(Routine(id = "r1", title = "Basura", type = RoutineType.HOUSEHOLD))
        fakeRepository.addHouseholdRoutine(Routine(id = "r2", title = "Fregar", type = RoutineType.HOUSEHOLD))
        fakeRepository.stubCompletions = mapOf(
            "r1" to listOf(
                RoutineCompletion(monday, "ana"),
                RoutineCompletion(monday.plusDays(1), "beto")
            ),
            "r2" to listOf(
                RoutineCompletion(monday.plusDays(2), "ana")
            )
        )

        val balance = getBalance(userId, householdId, monday, monday.plusDays(6)).getOrNull()!!

        assertEquals(2, balance["ana"])
        assertEquals(1, balance["beto"])
        assertNull(balance["carla"])
    }

    @Test
    fun `completions outside the range do not count`() = runBlocking {
        fakeRepository.addHouseholdRoutine(Routine(id = "r1", title = "Basura", type = RoutineType.HOUSEHOLD))
        fakeRepository.stubCompletions = mapOf(
            "r1" to listOf(
                RoutineCompletion(monday.minusDays(3), "ana"),
                RoutineCompletion(monday.plusDays(1), "ana")
            )
        )

        val balance = getBalance(userId, householdId, monday, monday.plusDays(6)).getOrNull()!!

        assertEquals(1, balance["ana"])
    }

    @Test
    fun `completions without an author are ignored`() = runBlocking {
        fakeRepository.addHouseholdRoutine(Routine(id = "r1", title = "Basura", type = RoutineType.HOUSEHOLD))
        fakeRepository.stubCompletions = mapOf(
            "r1" to listOf(RoutineCompletion(monday, ""))
        )

        val balance = getBalance(userId, householdId, monday, monday.plusDays(6)).getOrNull()!!

        assertTrue(balance.isEmpty())
    }

    @Test
    fun `a blank household returns an empty balance`() = runBlocking {
        val result = getBalance(userId, "", monday, monday.plusDays(6))

        assertTrue(result.getOrNull()!!.isEmpty())
    }
}
