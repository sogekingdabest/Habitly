package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakePantryRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GetContextualQuickPromptsUseCaseTest {

    private val fakeAuthRepo = FakeAuthRepository()
    private val fakeHouseholdRepo = FakeHouseholdRepository()
    private val fakeRoutinesRepo = FakeRoutinesRepository()
    private val fakeShoppingRepo = FakeShoppingRepository()
    private val fakePantryRepo = FakePantryRepository()

    private lateinit var useCase: GetContextualQuickPromptsUseCase

    /** 2026-07-13 lunes (día de planificación); 2026-07-15 miércoles (día normal). */
    private val monday = LocalDate.of(2026, 7, 13)
    private val wednesday = LocalDate.of(2026, 7, 15)

    @Before
    fun setUp() {
        useCase = GetContextualQuickPromptsUseCase(
            authRepository = fakeAuthRepo,
            householdRepository = fakeHouseholdRepo,
            routinesRepository = fakeRoutinesRepo,
            shoppingRepository = fakeShoppingRepo,
            pantryRepository = fakePantryRepo,
            generateWeeklyMenuUseCase = GenerateWeeklyMenuUseCase()
        )
    }

    @After
    fun tearDown() {
        fakeAuthRepo.reset()
        fakeHouseholdRepo.reset()
        fakeRoutinesRepo.reset()
        fakeShoppingRepo.reset()
        fakePantryRepo.reset()
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

    private fun epochOf(date: LocalDate): Long =
        date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun labelsOn(date: LocalDate): List<String> = runBlocking {
        useCase(today = date).map { it.label }
    }

    // ---------- Degradación ----------

    @Test
    fun `without session still returns the static prompts`() {
        val labels = labelsOn(wednesday)

        assertTrue(labels.isNotEmpty())
        assertTrue(labels.contains("Cena rápida"))
    }

    @Test
    fun `without household still returns the static prompts`() {
        fakeAuthRepo.stubCurrentUser = AuthUser(
            uid = "user1",
            email = "test@test.com",
            displayName = "Test User",
            isEmailVerified = true,
            authToken = AuthToken("fake", "fake")
        )

        val labels = labelsOn(wednesday)

        assertTrue(labels.isNotEmpty())
        assertFalse("sin casa no se puede saber si la lista está vacía", labels.contains("Lista semanal"))
    }

    @Test
    fun `never returns more prompts than the cap`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(Routine(title = "Gimnasio"))

        val labels = labelsOn(monday)

        assertTrue(labels.size <= GetContextualQuickPromptsUseCase.MAX_PROMPTS)
    }

    @Test
    fun `does not repeat labels`() {
        givenLoggedInWithHousehold()

        val labels = labelsOn(monday)

        assertEquals(labels.size, labels.distinct().size)
    }

    // ---------- Día de la semana ----------

    @Test
    fun `on a planning day the weekly menu is promoted to the first chip`() {
        givenLoggedInWithHousehold()
        // Lista a medias: no dispara ni "Lista semanal" ni "Recetas con mi lista".
        fakeShoppingRepo.stubItems = listOf(ShoppingItem(name = "Leche"))

        assertEquals("Menú semanal", labelsOn(monday).first())
    }

    @Test
    fun `on a normal day the weekly menu is not the first chip`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = listOf(ShoppingItem(name = "Leche"))

        val labels = labelsOn(wednesday)

        assertTrue("sigue disponible", labels.contains("Menú semanal"))
        assertFalse("pero no destacado", labels.first() == "Menú semanal")
    }

    // ---------- Estado de la lista ----------

    @Test
    fun `with an empty list suggests building the weekly list`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = emptyList()

        val labels = labelsOn(wednesday)

        assertTrue(labels.toString(), labels.contains("Lista semanal"))
        assertFalse(labels.contains("Recetas con mi lista"))
    }

    @Test
    fun `with a full list suggests cooking from it`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = (1..GetContextualQuickPromptsUseCase.MANY_ITEMS)
            .map { ShoppingItem(name = "Producto $it") }

        val labels = labelsOn(wednesday)

        assertTrue(labels.toString(), labels.contains("Recetas con mi lista"))
        assertFalse(labels.contains("Lista semanal"))
    }

    @Test
    fun `checked items do not count as pending for the full list rule`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = (1..GetContextualQuickPromptsUseCase.MANY_ITEMS)
            .map { ShoppingItem(name = "Producto $it", isChecked = true) }

        val labels = labelsOn(wednesday)

        assertFalse(labels.contains("Recetas con mi lista"))
        assertTrue("todo comprado equivale a lista vacía", labels.contains("Lista semanal"))
    }

    // ---------- Estado de la despensa ----------

    @Test
    fun `with a stocked pantry suggests cooking from it`() {
        givenLoggedInWithHousehold()
        fakePantryRepo.stubItems = (1..GetContextualQuickPromptsUseCase.ENOUGH_PANTRY)
            .map { PantryItem(id = "p$it", name = "Producto $it") }

        val labels = labelsOn(wednesday)

        assertTrue(labels.toString(), labels.contains("Cocinar con lo que tengo"))
    }

    @Test
    fun `with an almost empty pantry does not suggest cooking from it`() {
        givenLoggedInWithHousehold()
        fakePantryRepo.stubItems = listOf(PantryItem(id = "p1", name = "Sal"))

        val labels = labelsOn(wednesday)

        assertFalse(labels.contains("Cocinar con lo que tengo"))
    }

    // ---------- Estado de las rutinas ----------

    @Test
    fun `with routines pending today suggests organising the day`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(Routine(title = "Gimnasio", lastCompletedAt = null))

        val labels = labelsOn(wednesday)

        assertTrue(labels.toString(), labels.contains("Organiza mi día"))
    }

    @Test
    fun `with every routine already done today does not suggest organising`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(title = "Gimnasio", lastCompletedAt = epochOf(wednesday))
        )

        val labels = labelsOn(wednesday)

        assertFalse(labels.contains("Organiza mi día"))
    }

    @Test
    fun `household routines also count as pending`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubHouseholdRoutines = listOf(
            Routine(title = "Fregar el suelo", lastCompletedAt = null)
        )

        val labels = labelsOn(wednesday)

        assertTrue(labels.toString(), labels.contains("Organiza mi día"))
    }
}
