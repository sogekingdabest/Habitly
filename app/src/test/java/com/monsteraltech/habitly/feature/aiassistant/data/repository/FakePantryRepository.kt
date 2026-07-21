package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.PantryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakePantryRepository : PantryRepository {

    var stubItems: List<PantryItem> = emptyList()

    override fun observePantry(householdId: String): Flow<List<PantryItem>> = flowOf(stubItems)

    override suspend fun upsertItems(householdId: String, items: List<PantryItem>): Result<Unit> =
        Result.success(Unit)

    override suspend fun adjustQuantity(householdId: String, itemId: String, delta: Int): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteItem(householdId: String, itemId: String): Result<Unit> =
        Result.success(Unit)

    fun reset() {
        stubItems = emptyList()
    }
}
