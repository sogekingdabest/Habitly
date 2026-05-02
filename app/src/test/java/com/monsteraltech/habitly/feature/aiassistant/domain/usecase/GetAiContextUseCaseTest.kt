package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAiContextUseCaseTest {

    private val fakeAuthRepo = FakeAuthRepository()
    private val fakeHouseholdRepo = FakeHouseholdRepository()
    private val fakeRoutinesRepo = FakeRoutinesRepository()
    private val fakeShoppingRepo = FakeShoppingRepository()

    private lateinit var useCase: GetAiContextUseCase

    @Before
    fun setUp() {
        useCase = GetAiContextUseCase(
            authRepository = fakeAuthRepo,
            householdRepository = fakeHouseholdRepo,
            routinesRepository = fakeRoutinesRepo,
            shoppingRepository = fakeShoppingRepo
        )
    }

    @After
    fun tearDown() {
        fakeAuthRepo.reset()
        fakeHouseholdRepo.reset()
        fakeRoutinesRepo.reset()
        fakeShoppingRepo.reset()
    }

    private fun testUser(uid: String = "user1") = AuthUser(
        uid = uid,
        email = "test@test.com",
        displayName = "Test User",
        isEmailVerified = true,
        authToken = AuthToken("fake", "fake")
    )

    @Test
    fun `when no user is logged in, returns base personality only`() = runBlocking {
        val result = useCase()

        assertTrue(result.contains("Eres Habitly"))
        assertFalse(result.contains("Contexto Oculto"))
    }

    @Test
    fun `when user has no household, returns base personality only`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()

        val result = useCase()

        assertTrue(result.contains("Eres Habitly"))
        assertFalse(result.contains("Contexto Oculto"))
    }

    @Test
    fun `when user has household but no data, returns empty context`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")

        val result = useCase()

        assertTrue(result.contains("Contexto Oculto"))
        assertTrue(result.contains("lista de la compra está vacía"))
        assertTrue(result.contains("No tienes rutinas asignadas"))
    }

    @Test
    fun `when user has shopping items, includes them in context`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")
        fakeShoppingRepo.stubItems = listOf(
            ShoppingItem(name = "Leche", isChecked = false),
            ShoppingItem(name = "Pan", isChecked = true)
        )

        val result = useCase()

        assertTrue(result.contains("Leche"))
        assertTrue(result.contains("Pan"))
        assertTrue(result.contains("(pendiente)"))
        assertTrue(result.contains("(comprado)"))
    }

    @Test
    fun `when user has routines, includes them in context`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(title = "Limpiar cocina", lastCompletedAt = null),
            Routine(title = "Sacar basura", lastCompletedAt = 1000L)
        )

        val result = useCase()

        assertTrue(result.contains("Limpiar cocina"))
        assertTrue(result.contains("Sacar basura"))
        assertTrue(result.contains("(pendiente)"))
        assertTrue(result.contains("(marcada)"))
    }

    @Test
    fun `when timeout is very short, falls back to base personality`() = runBlocking {
        fakeAuthRepo.stubCurrentUser = testUser()
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")

        val result = useCase(timeoutMs = 1)

        assertTrue(result.contains("Eres Habitly"))
    }

    @Test
    fun `base personality includes Habitly name and role`() = runBlocking {
        val result = useCase()

        assertTrue(result.contains("Habitly"))
        assertTrue(result.contains("gestión del hogar"))
    }
}
