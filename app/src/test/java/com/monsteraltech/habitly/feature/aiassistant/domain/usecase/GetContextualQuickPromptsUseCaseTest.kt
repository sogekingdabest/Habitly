package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakePantryRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.QuickPromptId
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
            pantryRepository = fakePantryRepo
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

    private fun idsOn(date: LocalDate): List<QuickPromptId> = runBlocking {
        useCase(today = date).map { it.id }
    }

    // ---------- Degradación ----------

    @Test
    fun `without session still returns the static prompts`() {
        val ids = idsOn(wednesday)

        assertTrue(ids.isNotEmpty())
        assertTrue(ids.contains(QuickPromptId.QUICK_DINNER))
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

        val ids = idsOn(wednesday)

        assertTrue(ids.isNotEmpty())
        assertFalse("sin casa no se puede saber si la lista está vacía", ids.contains(QuickPromptId.WEEKLY_LIST))
    }

    @Test
    fun `never returns more prompts than the cap`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(Routine(title = "Gimnasio"))

        val ids = idsOn(monday)

        assertTrue(ids.size <= GetContextualQuickPromptsUseCase.MAX_PROMPTS)
    }

    @Test
    fun `does not repeat prompts`() {
        givenLoggedInWithHousehold()

        val ids = idsOn(monday)

        assertEquals(ids.size, ids.distinct().size)
    }

    // ---------- Día de la semana ----------

    @Test
    fun `on a planning day the weekly menu is promoted to the first chip`() {
        givenLoggedInWithHousehold()
        // Lista a medias: no dispara ni "Lista semanal" ni "Recetas con mi lista".
        fakeShoppingRepo.stubItems = listOf(ShoppingItem(name = "Leche"))

        assertEquals(QuickPromptId.WEEKLY_MENU, idsOn(monday).first())
    }

    @Test
    fun `on a normal day the weekly menu is not the first chip`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = listOf(ShoppingItem(name = "Leche"))

        val ids = idsOn(wednesday)

        assertTrue("sigue disponible", ids.contains(QuickPromptId.WEEKLY_MENU))
        assertFalse("pero no destacado", ids.first() == QuickPromptId.WEEKLY_MENU)
    }

    // ---------- Estado de la lista ----------

    @Test
    fun `with an empty list suggests building the weekly list`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = emptyList()

        val ids = idsOn(wednesday)

        assertTrue(ids.toString(), ids.contains(QuickPromptId.WEEKLY_LIST))
        assertFalse(ids.contains(QuickPromptId.RECIPES_FROM_LIST))
    }

    @Test
    fun `with a full list suggests cooking from it`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = (1..GetContextualQuickPromptsUseCase.MANY_ITEMS)
            .map { ShoppingItem(name = "Producto $it") }

        val ids = idsOn(wednesday)

        assertTrue(ids.toString(), ids.contains(QuickPromptId.RECIPES_FROM_LIST))
        assertFalse(ids.contains(QuickPromptId.WEEKLY_LIST))
    }

    @Test
    fun `checked items do not count as pending for the full list rule`() {
        givenLoggedInWithHousehold()
        fakeShoppingRepo.stubItems = (1..GetContextualQuickPromptsUseCase.MANY_ITEMS)
            .map { ShoppingItem(name = "Producto $it", isChecked = true) }

        val ids = idsOn(wednesday)

        assertFalse(ids.contains(QuickPromptId.RECIPES_FROM_LIST))
        assertTrue("todo comprado equivale a lista vacía", ids.contains(QuickPromptId.WEEKLY_LIST))
    }

    // ---------- Estado de la despensa ----------

    @Test
    fun `with a stocked pantry suggests cooking from it`() {
        givenLoggedInWithHousehold()
        fakePantryRepo.stubItems = (1..GetContextualQuickPromptsUseCase.ENOUGH_PANTRY)
            .map { PantryItem(id = "p$it", name = "Producto $it") }

        val ids = idsOn(wednesday)

        assertTrue(ids.toString(), ids.contains(QuickPromptId.COOK_FROM_PANTRY))
    }

    @Test
    fun `with an almost empty pantry does not suggest cooking from it`() {
        givenLoggedInWithHousehold()
        fakePantryRepo.stubItems = listOf(PantryItem(id = "p1", name = "Sal"))

        val ids = idsOn(wednesday)

        assertFalse(ids.contains(QuickPromptId.COOK_FROM_PANTRY))
    }

    // ---------- Estado de las rutinas ----------

    @Test
    fun `with routines pending today suggests organising the day`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(Routine(title = "Gimnasio", lastCompletedAt = null))

        val ids = idsOn(wednesday)

        assertTrue(ids.toString(), ids.contains(QuickPromptId.ORGANIZE_DAY))
    }

    @Test
    fun `with every routine already done today does not suggest organising`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubRoutines = listOf(
            Routine(title = "Gimnasio", lastCompletedAt = epochOf(wednesday))
        )

        val ids = idsOn(wednesday)

        assertFalse(ids.contains(QuickPromptId.ORGANIZE_DAY))
    }

    @Test
    fun `household routines also count as pending`() {
        givenLoggedInWithHousehold()
        fakeRoutinesRepo.stubHouseholdRoutines = listOf(
            Routine(title = "Fregar el suelo", lastCompletedAt = null)
        )

        val ids = idsOn(wednesday)

        assertTrue(ids.toString(), ids.contains(QuickPromptId.ORGANIZE_DAY))
    }
}
