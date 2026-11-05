package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.household.domain.model.UserProfile
import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The seam where a suggestion becomes a real product, and so the last chance to keep a category
 * the app does not have out of the shopping list.
 */
class AddAiItemsToShoppingListUseCaseTest {

    private val fakeAuthRepo = FakeAuthRepository()
    private val fakeHouseholdRepo = FakeHouseholdRepository()
    private val fakeShoppingRepo = FakeShoppingRepository()

    private lateinit var useCase: AddAiItemsToShoppingListUseCase

    @Before
    fun setUp() {
        useCase = AddAiItemsToShoppingListUseCase(
            authRepository = fakeAuthRepo,
            householdRepository = fakeHouseholdRepo,
            shoppingRepository = fakeShoppingRepo
        )
        fakeAuthRepo.stubCurrentUser = AuthUser(
            uid = "user1",
            email = "test@test.com",
            displayName = "Test User",
            isEmailVerified = true,
            authToken = AuthToken("fake", "fake")
        )
        fakeHouseholdRepo.stubProfile = UserProfile(id = "user1", activeHouseholdId = "house1")
    }

    @After
    fun tearDown() {
        fakeAuthRepo.reset()
        fakeHouseholdRepo.reset()
        fakeShoppingRepo.reset()
    }

    @Test
    fun `the model's key becomes the category the picker offers`() = runBlocking {
        val result = useCase(
            listOf(AiShoppingSuggestion(name = "Leche", quantity = 1, unit = "L", category = "lacteos"))
        )

        assertEquals(1, result.getOrNull())
        assertEquals("Lácteos", fakeShoppingRepo.addedItems.single().category)
    }

    @Test
    fun `a category the app does not have does not reach the product`() = runBlocking {
        // Lo que venía pasando: el prompt dictaba categorías que no existen y se escribían tal
        // cual, de modo que la despensa acababa con un grupo «Despensa y Conservas» propio.
        useCase(
            listOf(AiShoppingSuggestion(name = "Arroz", quantity = 1, unit = "kg", category = "Despensa y Conservas"))
        )

        assertEquals("", fakeShoppingRepo.addedItems.single().category)
    }

    @Test
    fun `the rest of the product is passed through untouched`() = runBlocking {
        useCase(
            listOf(AiShoppingSuggestion(name = "Tomate", quantity = 6, unit = "unidad", category = "frutas"))
        )

        val item = fakeShoppingRepo.addedItems.single()
        assertEquals("Tomate", item.name)
        assertEquals(6, item.quantity)
        assertEquals("unidad", item.unit)
        assertEquals("user1", item.authorId)
    }
}
