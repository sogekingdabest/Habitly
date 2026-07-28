package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Adds the items the AI proposed to the active household's shopping list. It resolves the user and
 * their active household internally so the ViewModel stays decoupled from Firebase.
 *
 * @return how many items were added, or [Result.failure] if there is no session or household, or
 *   the write fails.
 */
class AddAiItemsToShoppingListUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val shoppingRepository: ShoppingRepository
) {
    suspend operator fun invoke(items: List<AiShoppingSuggestion>): Result<Int> {
        if (items.isEmpty()) return Result.success(0)

        val user = authRepository.getCurrentUser()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado"))

        val profile = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }
        val householdId = profile?.activeHouseholdId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No hay una casa activa"))

        val shoppingItems = items.map { suggestion ->
            ShoppingItem(
                name = suggestion.name,
                store = ANY_STORE,
                authorId = user.uid,
                quantity = suggestion.quantity,
                unit = suggestion.unit,
                category = suggestion.category
            )
        }

        return shoppingRepository.addShoppingItems(householdId, shoppingItems).map { items.size }
    }

    private companion object {
        const val PROFILE_TIMEOUT_MS = 3000L
        const val ANY_STORE = "Cualquiera"
    }
}
