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
