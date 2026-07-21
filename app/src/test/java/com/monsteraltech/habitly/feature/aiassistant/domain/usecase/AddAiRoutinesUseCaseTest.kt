package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.routines.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.AddRoutineUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class AddAiRoutinesUseCaseTest {

    private val fakeAuthRepo = FakeAuthRepository()
    private val fakeHouseholdRepo = FakeHouseholdRepository()
    private val fakeRoutinesRepo = FakeRoutinesRepository()

    private lateinit var useCase: AddAiRoutinesUseCase

    @Before
    fun setUp() {
        useCase = AddAiRoutinesUseCase(
            authRepository = fakeAuthRepo,
            householdRepository = fakeHouseholdRepo,
            addRoutineUseCase = AddRoutineUseCase(fakeRoutinesRepo)
        )
    }

    @After
    fun tearDown() {
        fakeAuthRepo.reset()
        fakeHouseholdRepo.reset()
        fakeRoutinesRepo.reset()
    }

    private fun givenLoggedInWithHousehold() {
        fakeAuthRepo.stubCurrentUser = AuthUser(
            uid = "user1",
            email = "test@test.com",
            displayName = "Test User",
            isEmailVerified = true,
            authToken = AuthToken("fake", "fake")
        )
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")
    }

    private val sample = listOf(
        AiRoutineSuggestion(title = "Fregar la cocina", frequency = RoutineFrequency.DAILY),
        AiRoutineSuggestion(
            title = "Cambiar sábanas",
            frequency = RoutineFrequency.EVERY_N_DAYS,
            intervalDays = 10
        )
    )

    @Test
    fun `empty list succeeds without creating anything`() = runBlocking {
        givenLoggedInWithHousehold()

        val result = useCase(emptyList(), RoutineType.PERSONAL)

        assertEquals(0, result.getOrNull())
        assertEquals(0, fakeRoutinesRepo.addRoutineCalls)
    }

    @Test
    fun `without session it fails`() = runBlocking {
        val result = useCase(sample, RoutineType.PERSONAL)

        assertTrue(result.isFailure)
    }

    @Test
    fun `without an active household it fails`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = AuthUser(
            uid = "user1",
            email = "test@test.com",
            displayName = "Test User",
            isEmailVerified = true,
            authToken = AuthToken("fake", "fake")
        )
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "")

        val result = useCase(sample, RoutineType.PERSONAL)

        assertTrue(result.isFailure)
    }

    @Test
    fun `creates the personal routines and reports the count`() = runBlocking {
        givenLoggedInWithHousehold()

        val result = useCase(sample, RoutineType.PERSONAL)

        assertEquals(2, result.getOrNull())
        val created = fakeRoutinesRepo.observePersonalRoutines("user1").first()
        assertEquals(2, created.size)
        assertEquals("Fregar la cocina", created[0].title)
    }

    @Test
    fun `creates household routines when that type is chosen`() = runBlocking {
        givenLoggedInWithHousehold()

        useCase(sample, RoutineType.HOUSEHOLD)

        val created = fakeRoutinesRepo.observeHouseholdRoutines("house1").first()
        assertEquals(2, created.size)
        assertTrue(created.all { it.type == RoutineType.HOUSEHOLD })
    }

    @Test
    fun `keeps frequency, days and interval`() = runBlocking {
        givenLoggedInWithHousehold()
        val weekly = listOf(
            AiRoutineSuggestion(
                title = "Sacar basura",
                frequency = RoutineFrequency.WEEKLY,
                scheduledDays = listOf(Calendar.MONDAY, Calendar.THURSDAY)
            )
        )

        useCase(weekly + sample[1], RoutineType.PERSONAL)

        val created = fakeRoutinesRepo.observePersonalRoutines("user1").first()
        val basura = created.first { it.title == "Sacar basura" }
        val sabanas = created.first { it.title == "Cambiar sábanas" }

        assertEquals(RoutineFrequency.WEEKLY, basura.frequency)
        assertEquals(listOf(Calendar.MONDAY, Calendar.THURSDAY), basura.scheduledDays)
        assertEquals(RoutineFrequency.EVERY_N_DAYS, sabanas.frequency)
        assertEquals(10, sabanas.intervalDays)
    }

    @Test
    fun `routines are created without a reminder`() = runBlocking {
        givenLoggedInWithHousehold()

        useCase(sample, RoutineType.PERSONAL)

        val created = fakeRoutinesRepo.observePersonalRoutines("user1").first()
        assertTrue(created.all { it.reminderTime == null })
    }

    @Test
    fun `when every creation fails it returns failure`() = runBlocking {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.shouldFail = true

        val result = useCase(sample, RoutineType.PERSONAL)

        assertTrue(result.isFailure)
    }
}
