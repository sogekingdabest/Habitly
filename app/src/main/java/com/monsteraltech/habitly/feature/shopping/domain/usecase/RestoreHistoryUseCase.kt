package com.monsteraltech.habitly.feature.shopping.domain.usecase

import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import javax.inject.Inject

class RestoreHistoryUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, historyId: String): Result<Unit> {
        return repository.restoreHistory(householdId, historyId)
    }
}
