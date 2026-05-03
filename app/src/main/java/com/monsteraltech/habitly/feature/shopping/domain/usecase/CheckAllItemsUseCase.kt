package com.monsteraltech.habitly.feature.shopping.domain.usecase

import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import javax.inject.Inject

class CheckAllItemsUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, itemIds: List<String>, check: Boolean): Result<Unit> {
        if (itemIds.isEmpty()) return Result.success(Unit)
        return repository.bulkToggleItems(householdId, itemIds, check)
    }
}
