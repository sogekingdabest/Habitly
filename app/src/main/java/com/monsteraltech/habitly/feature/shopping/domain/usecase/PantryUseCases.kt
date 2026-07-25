package com.monsteraltech.habitly.feature.shopping.domain.usecase

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.PantryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePantryUseCase @Inject constructor(
    private val repository: PantryRepository
) {
    operator fun invoke(householdId: String): Flow<List<PantryItem>> =
        repository.observePantry(householdId)
}

class AdjustPantryQuantityUseCase @Inject constructor(
    private val repository: PantryRepository
) {
    suspend operator fun invoke(householdId: String, itemId: String, delta: Int): Result<Unit> =
        repository.adjustQuantity(householdId, itemId, delta)
}

class DeletePantryItemUseCase @Inject constructor(
    private val repository: PantryRepository
) {
    suspend operator fun invoke(householdId: String, itemId: String): Result<Unit> =
        repository.deleteItem(householdId, itemId)
}

/**
 * Devuelve a la despensa un producto que se acaba de sacar (el "deshacer" del gesto).
 *
 * `upsertItems` suma a lo que hubiera, y tras el borrado no hay nada, así que el producto
 * vuelve con la misma cantidad que tenía.
 */
class RestorePantryItemUseCase @Inject constructor(
    private val repository: PantryRepository
) {
    suspend operator fun invoke(householdId: String, item: PantryItem): Result<Unit> =
        repository.upsertItems(householdId, listOf(item))
}
