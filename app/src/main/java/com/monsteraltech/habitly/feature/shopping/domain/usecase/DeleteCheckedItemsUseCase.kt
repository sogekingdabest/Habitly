package com.monsteraltech.habitly.feature.shopping.domain.usecase

import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import javax.inject.Inject

class DeleteCheckedItemsUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String): Result<Unit> {
        return repository.deleteCheckedItems(householdId)
    }
}
