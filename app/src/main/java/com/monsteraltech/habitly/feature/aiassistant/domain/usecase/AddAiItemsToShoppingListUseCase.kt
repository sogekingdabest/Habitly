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
 * Añade a la lista de la compra de la casa activa los productos que ha propuesto la IA.
 * Resuelve internamente el usuario y su casa activa para no acoplar el ViewModel a Firebase.
 *
 * @return número de productos añadidos, o [Result.failure] si no hay sesión/casa o falla la escritura.
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
