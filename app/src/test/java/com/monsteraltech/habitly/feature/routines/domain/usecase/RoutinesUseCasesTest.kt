package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class RoutinesUseCasesTest {

    private val fakeRepository = FakeRoutinesRepository()

    private lateinit var observeUseCase: ObserveRoutinesUseCase
    private lateinit var addUseCase: AddRoutineUseCase
    private lateinit var toggleUseCase: ToggleRoutineUseCase
    private lateinit var deleteUseCase: DeleteRoutineUseCase

    private val userId = "user1"
    private val householdId = "house1"

    @Before
    fun setUp() {
        observeUseCase = ObserveRoutinesUseCase(fakeRepository)
        addUseCase = AddRoutineUseCase(fakeRepository)
        toggleUseCase = ToggleRoutineUseCase(fakeRepository)
        deleteUseCase = DeleteRoutineUseCase(fakeRepository)
    }

    @After
    fun tearDown() {
        fakeRepository.reset()
    }

    // --- ObserveRoutinesUseCase ---

    @Test
    fun `observe combines personal and household routines`() = runBlocking {
        val personalRoutine = Routine(id = "p1", title = "Personal 1", type = RoutineType.PERSONAL, authorId = userId)
        val householdRoutine = Routine(id = "h1", title = "Household 1", type = RoutineType.HOUSEHOLD, authorId = userId)

        fakeRepository.addPersonalRoutine(personalRoutine)
        fakeRepository.addHouseholdRoutine(householdRoutine)

        val result = observeUseCase(userId, householdId).first()

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "p1" })
        assertTrue(result.any { it.id == "h1" })
    }

    @Test
    fun `observe returns empty list when no routines exist`() = runBlocking {
        val result = observeUseCase(userId, householdId).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observe emits updates when routines change`() = runBlocking {
        val initialRoutines = observeUseCase(userId, householdId).first()
        assertTrue(initialRoutines.isEmpty())

        val newRoutine = Routine(id = "p1", title = "New Routine", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(newRoutine)

        val updatedRoutines = observeUseCase(userId, householdId).first()

        assertEquals(1, updatedRoutines.size)
        assertEquals("New Routine", updatedRoutines[0].title)
    }

    // --- AddRoutineUseCase ---

    @Test
    fun `add routine with blank title returns failure`() = runBlocking {
        val result = addUseCase(userId, householdId, "", "description", RoutineType.PERSONAL)

        assertTrue(result.isFailure)
        assertEquals("El título no puede estar vacío", result.exceptionOrNull()?.message)
    }

    @Test
    fun `add routine with whitespace-only title returns failure`() = runBlocking {
        val result = addUseCase(userId, householdId, "   ", "description", RoutineType.PERSONAL)

        assertTrue(result.isFailure)
    }

    @Test
    fun `add routine trims title and description`() = runBlocking {
        val result = addUseCase(userId, householdId, "  My Routine  ", "  My description  ", RoutineType.PERSONAL)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.addRoutineCalls)
    }

    @Test
    fun `add personal routine generates valid ID and sets author`() = runBlocking {
        val result = addUseCase(userId, householdId, "Morning Routine", "Start the day right", RoutineType.PERSONAL)

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals(1, routines.size)

        val routine = routines[0]
        assertNotNull(routine.id)
        assertEquals("Morning Routine", routine.title)
        assertEquals("Start the day right", routine.description)
        assertEquals(RoutineType.PERSONAL, routine.type)
        assertEquals(userId, routine.authorId)
        assertEquals(RoutineFrequency.DAILY, routine.frequency)
    }

    @Test
    fun `add household routine sets correct type`() = runBlocking {
        val result = addUseCase(userId, householdId, "Clean Kitchen", "Deep clean", RoutineType.HOUSEHOLD)

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertEquals(1, routines.size)
        assertEquals(RoutineType.HOUSEHOLD, routines[0].type)
    }

    @Test
    fun `add routine when repository fails returns failure`() = runBlocking {
        fakeRepository.shouldFail = true
        fakeRepository.errorMessage = "Network error"

        val result = addUseCase(userId, householdId, "Test", "desc", RoutineType.PERSONAL)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    // --- ToggleRoutineUseCase ---

    @Test
    fun `toggle routine marks as completed with timestamp and userId`() = runBlocking {
        val routine = Routine(id = "r1", title = "Test", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(routine)

        val result = toggleUseCase(userId, householdId, routine, true)

        assertTrue(result.isSuccess)
        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals(1, updated.size)
        assertNotNull(updated[0].lastCompletedAt)
        assertEquals(userId, updated[0].lastCompletedBy)
    }

    @Test
    fun `toggle routine unmarks completion when isCompleted is false`() = runBlocking {
        val routine = Routine(
            id = "r1",
            title = "Test",
            type = RoutineType.PERSONAL,
            authorId = userId,
            lastCompletedAt = System.currentTimeMillis(),
            lastCompletedBy = userId
        )
        fakeRepository.addPersonalRoutine(routine)

        val result = toggleUseCase(userId, householdId, routine, false)

        assertTrue(result.isSuccess)
        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertNull(updated[0].lastCompletedAt)
        assertNull(updated[0].lastCompletedBy)
    }

    @Test
    fun `toggle household routine tracks who completed it`() = runBlocking {
        val routine = Routine(id = "r1", title = "Test", type = RoutineType.HOUSEHOLD, authorId = userId)
        fakeRepository.addHouseholdRoutine(routine)

        toggleUseCase(userId, householdId, routine, true)

        val updated = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertEquals(userId, updated[0].lastCompletedBy)
    }

    @Test
    fun `toggle routine when repository fails returns failure`() = runBlocking {
        fakeRepository.shouldFail = true
        val routine = Routine(id = "r1", title = "Test", type = RoutineType.PERSONAL, authorId = userId)

        val result = toggleUseCase(userId, householdId, routine, true)

        assertTrue(result.isFailure)
    }

    // --- DeleteRoutineUseCase ---

    @Test
    fun `delete personal routine removes it from list`() = runBlocking {
        val routine = Routine(id = "r1", title = "To Delete", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(routine)

        val result = deleteUseCase(userId, householdId, routine)

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observePersonalRoutines(userId).first()
        assertTrue(routines.isEmpty())
    }

    @Test
    fun `delete household routine removes it from list`() = runBlocking {
        val routine = Routine(id = "r1", title = "To Delete", type = RoutineType.HOUSEHOLD, authorId = userId)
        fakeRepository.addHouseholdRoutine(routine)

        val result = deleteUseCase(userId, householdId, routine)

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertTrue(routines.isEmpty())
    }

    @Test
    fun `delete routine only removes the targeted routine`() = runBlocking {
        val routine1 = Routine(id = "r1", title = "Keep", type = RoutineType.PERSONAL, authorId = userId)
        val routine2 = Routine(id = "r2", title = "Delete", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(routine1)
        fakeRepository.addPersonalRoutine(routine2)

        deleteUseCase(userId, householdId, routine2)

        val routines = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals(1, routines.size)
        assertEquals("Keep", routines[0].title)
    }

    @Test
    fun `delete routine when repository fails returns failure`() = runBlocking {
        fakeRepository.shouldFail = true
        val routine = Routine(id = "r1", title = "Test", type = RoutineType.PERSONAL, authorId = userId)

        val result = deleteUseCase(userId, householdId, routine)

        assertTrue(result.isFailure)
    }

    // --- UpdateRoutineUseCase ---

    private lateinit var updateUseCase: UpdateRoutineUseCase

    @Before
    fun setUpUpdateUseCase() {
        updateUseCase = UpdateRoutineUseCase(fakeRepository)
    }

    @Test
    fun `update routine with blank title returns failure`() = runBlocking {
        val routine = Routine(id = "r1", title = "Old Title", type = RoutineType.PERSONAL, authorId = userId)

        val result = updateUseCase(userId, householdId, routine, "", "New description")

        assertTrue(result.isFailure)
        assertEquals("El título no puede estar vacío", result.exceptionOrNull()?.message)
    }

    @Test
    fun `update routine changes title and description`() = runBlocking {
        val routine = Routine(id = "r1", title = "Old Title", description = "Old desc", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(routine)

        val result = updateUseCase(userId, householdId, routine, "New Title", "New description")

        assertTrue(result.isSuccess)
        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals(1, updated.size)
        assertEquals("New Title", updated[0].title)
        assertEquals("New description", updated[0].description)
    }

    @Test
    fun `update routine trims title and description`() = runBlocking {
        val routine = Routine(id = "r1", title = "Old", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(routine)

        updateUseCase(userId, householdId, routine, "  Trimmed Title  ", "  Trimmed Desc  ")

        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals("Trimmed Title", updated[0].title)
        assertEquals("Trimmed Desc", updated[0].description)
    }

    @Test
    fun `update routine preserves other fields`() = runBlocking {
        val routine = Routine(
            id = "r1",
            title = "Old",
            type = RoutineType.PERSONAL,
            authorId = userId,
            lastCompletedAt = System.currentTimeMillis(),
            lastCompletedBy = userId
        )
        fakeRepository.addPersonalRoutine(routine)

        updateUseCase(userId, householdId, routine, "New Title", "")

        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals("New Title", updated[0].title)
        assertEquals(userId, updated[0].authorId)
        assertNotNull(updated[0].lastCompletedAt)
        assertEquals(userId, updated[0].lastCompletedBy)
    }

    @Test
    fun `update household routine works correctly`() = runBlocking {
        val routine = Routine(id = "r1", title = "Old", type = RoutineType.HOUSEHOLD, authorId = userId)
        fakeRepository.addHouseholdRoutine(routine)

        updateUseCase(userId, householdId, routine, "New Household Title", "")

        val updated = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertEquals("New Household Title", updated[0].title)
    }

    @Test
    fun `update routine when repository fails returns failure`() = runBlocking {
        fakeRepository.shouldFail = true
        val routine = Routine(id = "r1", title = "Test", type = RoutineType.PERSONAL, authorId = userId)

        val result = updateUseCase(userId, householdId, routine, "New Title", "New desc")

        assertTrue(result.isFailure)
    }

    @Test
    fun `update routine changes frequency and scheduledDays`() = runBlocking {
        val routine = Routine(id = "r1", title = "Test", type = RoutineType.PERSONAL, authorId = userId)
        fakeRepository.addPersonalRoutine(routine)

        val days = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
        updateUseCase(userId, householdId, routine, "Test", "", RoutineFrequency.WEEKLY, days)

        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals(RoutineFrequency.WEEKLY, updated[0].frequency)
        assertEquals(days, updated[0].scheduledDays)
    }

    // --- Icono y nivel de notificación (Fase 2) ---

    @Test
    fun `add routine stores icon and notification level`() = runBlocking {
        addUseCase(
            userId = userId,
            householdId = householdId,
            title = "Colada",
            description = "",
            type = RoutineType.PERSONAL,
            icon = "🧺",
            notificationLevel = NotificationLevel.HIGH
        )

        val stored = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals("🧺", stored[0].icon)
        assertEquals(NotificationLevel.HIGH, stored[0].notificationLevel)
    }

    @Test
    fun `add routine defaults to no icon and the default level`() = runBlocking {
        addUseCase(userId, householdId, "Sin icono", "", RoutineType.PERSONAL)

        val stored = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals("", stored[0].icon)
        assertEquals(NotificationLevel.DEFAULT, stored[0].notificationLevel)
    }

    @Test
    fun `update routine changes icon and notification level`() = runBlocking {
        val routine = Routine(
            id = "r1",
            title = "Test",
            type = RoutineType.PERSONAL,
            authorId = userId,
            icon = "🧹",
            notificationLevel = NotificationLevel.DEFAULT
        )
        fakeRepository.addPersonalRoutine(routine)

        updateUseCase(
            userId = userId,
            householdId = householdId,
            routine = routine,
            title = "Test",
            description = "",
            icon = "🐕",
            notificationLevel = NotificationLevel.SILENT
        )

        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals("🐕", updated[0].icon)
        assertEquals(NotificationLevel.SILENT, updated[0].notificationLevel)
    }

    @Test
    fun `update routine keeps icon and level when not passed`() = runBlocking {
        val routine = Routine(
            id = "r1",
            title = "Test",
            type = RoutineType.PERSONAL,
            authorId = userId,
            icon = "🧺",
            notificationLevel = NotificationLevel.HIGH
        )
        fakeRepository.addPersonalRoutine(routine)

        // Renaming must not silently wipe the icon or drop the level back to default.
        updateUseCase(userId, householdId, routine, "Otro título", "")

        val updated = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals("🧺", updated[0].icon)
        assertEquals(NotificationLevel.HIGH, updated[0].notificationLevel)
    }

    @Test
    fun `add routine with weekly frequency stores scheduledDays`() = runBlocking {
        val days = listOf(Calendar.MONDAY, Calendar.FRIDAY)
        val result = addUseCase(userId, householdId, "Gym", "Leg day", RoutineType.PERSONAL, RoutineFrequency.WEEKLY, days)

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observePersonalRoutines(userId).first()
        assertEquals(1, routines.size)
        assertEquals(RoutineFrequency.WEEKLY, routines[0].frequency)
        assertEquals(days, routines[0].scheduledDays)
    }

    @Test
    fun `add routine with daily frequency has empty scheduledDays`() = runBlocking {
        val result = addUseCase(userId, householdId, "Meditate", "", RoutineType.PERSONAL, RoutineFrequency.DAILY)

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observePersonalRoutines(userId).first()
        assertTrue(routines[0].scheduledDays.isEmpty())
    }

    // --- ReorderRoutineUseCase ---

    private lateinit var reorderUseCase: ReorderRoutineUseCase

    @Before
    fun setUpReorderUseCase() {
        reorderUseCase = ReorderRoutineUseCase(fakeRepository)
    }

    @Test
    fun `reorder routines updates order field correctly`() = runBlocking {
        val routine1 = Routine(id = "r1", title = "First", type = RoutineType.PERSONAL, authorId = userId, order = 0)
        val routine2 = Routine(id = "r2", title = "Second", type = RoutineType.PERSONAL, authorId = userId, order = 1)
        fakeRepository.addPersonalRoutine(routine1)
        fakeRepository.addPersonalRoutine(routine2)

        val result = reorderUseCase(userId, householdId, RoutineType.PERSONAL, listOf("r2", "r1"))

        assertTrue(result.isSuccess)
        val routines = fakeRepository.observePersonalRoutines(userId).first()
        val r2 = routines.find { it.id == "r2" }
        val r1 = routines.find { it.id == "r1" }
        assertEquals(0, r2?.order)
        assertEquals(1, r1?.order)
    }

    @Test
    fun `reorder household routines works correctly`() = runBlocking {
        val routine1 = Routine(id = "r1", title = "First", type = RoutineType.HOUSEHOLD, authorId = userId, order = 0)
        val routine2 = Routine(id = "r2", title = "Second", type = RoutineType.HOUSEHOLD, authorId = userId, order = 1)
        fakeRepository.addHouseholdRoutine(routine1)
        fakeRepository.addHouseholdRoutine(routine2)

        reorderUseCase(userId, householdId, RoutineType.HOUSEHOLD, listOf("r2", "r1"))

        val routines = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertEquals(0, routines.find { it.id == "r2" }?.order)
        assertEquals(1, routines.find { it.id == "r1" }?.order)
    }

    @Test
    fun `reorder routines when repository fails returns failure`() = runBlocking {
        fakeRepository.shouldFail = true

        val result = reorderUseCase(userId, householdId, RoutineType.PERSONAL, listOf("r1", "r2"))

        assertTrue(result.isFailure)
    }
}
