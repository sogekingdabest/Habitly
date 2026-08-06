package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.model.MAX_COMMENT_LENGTH
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutineCommentsUseCasesTest {

    private val fakeRepository = FakeRoutinesRepository()

    private lateinit var addUseCase: AddRoutineCommentUseCase
    private lateinit var deleteUseCase: DeleteRoutineCommentUseCase
    private lateinit var observeUseCase: ObserveRoutineCommentsUseCase

    private val householdId = "house1"
    private val routineId = "r1"
    private val userId = "user1"

    @Before
    fun setUp() {
        addUseCase = AddRoutineCommentUseCase(fakeRepository)
        deleteUseCase = DeleteRoutineCommentUseCase(fakeRepository)
        observeUseCase = ObserveRoutineCommentsUseCase(fakeRepository)
        fakeRepository.addHouseholdRoutine(
            Routine(id = routineId, title = "Colada", type = RoutineType.HOUSEHOLD)
        )
    }

    // ---------- Validación ----------

    @Test
    fun `blank comment is rejected`() = runBlocking {
        val result = addUseCase(householdId, routineId, userId, "   ")

        assertTrue(result.isFailure)
        assertEquals(0, fakeRepository.addCommentCalls)
    }

    @Test
    fun `comment is trimmed before saving`() = runBlocking {
        addUseCase(householdId, routineId, userId, "  ya compré lejía  ")

        val stored = observeUseCase(householdId, routineId).first()
        assertEquals("ya compré lejía", stored[0].text)
    }

    @Test
    fun `comment longer than the limit is cut`() = runBlocking {
        addUseCase(householdId, routineId, userId, "x".repeat(MAX_COMMENT_LENGTH + 50))

        val stored = observeUseCase(householdId, routineId).first()
        assertEquals(MAX_COMMENT_LENGTH, stored[0].text.length)
    }

    @Test
    fun `comment without a household or author is rejected`() = runBlocking {
        assertTrue(addUseCase("", routineId, userId, "hola").isFailure)
        assertTrue(addUseCase(householdId, routineId, "", "hola").isFailure)
        assertEquals(0, fakeRepository.addCommentCalls)
    }

    @Test
    fun `comment records its author`() = runBlocking {
        addUseCase(householdId, routineId, userId, "hola")

        val stored = observeUseCase(householdId, routineId).first()
        assertEquals(userId, stored[0].authorId)
    }

    // ---------- Contador denormalizado ----------

    @Test
    fun `adding a comment bumps the routine counter`() = runBlocking {
        addUseCase(householdId, routineId, userId, "uno")
        addUseCase(householdId, routineId, userId, "dos")

        val routines = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertEquals(2, routines[0].commentCount)
    }

    @Test
    fun `deleting a comment brings the counter back down`() = runBlocking {
        addUseCase(householdId, routineId, userId, "uno")
        val stored = observeUseCase(householdId, routineId).first()

        deleteUseCase(householdId, routineId, stored[0].id)

        val routines = fakeRepository.observeHouseholdRoutines(householdId).first()
        assertEquals(0, routines[0].commentCount)
        assertTrue(observeUseCase(householdId, routineId).first().isEmpty())
    }

    @Test
    fun `a failing repository does not report success`() = runBlocking {
        fakeRepository.shouldFail = true

        val result = addUseCase(householdId, routineId, userId, "hola")

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
