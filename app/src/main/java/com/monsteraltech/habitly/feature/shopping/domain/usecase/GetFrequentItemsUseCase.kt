package com.monsteraltech.habitly.feature.shopping.domain.usecase

import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import javax.inject.Inject

class GetFrequentItemsUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, limit: Int = 10): Result<List<String>> {
        return repository.getFrequentItems(householdId, limit)
    }
}
