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
 * Puts a just-removed product back into the pantry — the gesture's "undo".
 *
 * `upsertItems` adds onto whatever is there, and after the deletion there is nothing, so the
 * product returns with exactly the quantity it had.
 */
class RestorePantryItemUseCase @Inject constructor(
    private val repository: PantryRepository
) {
    suspend operator fun invoke(householdId: String, item: PantryItem): Result<Unit> =
        repository.upsertItems(householdId, listOf(item))
}
