package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeShoppingRepository : ShoppingRepository {

    var stubItems: List<ShoppingItem> = emptyList()

    override fun observeShoppingList(householdId: String): Flow<List<ShoppingItem>> = flowOf(stubItems)

    override suspend fun addShoppingItem(householdId: String, name: String, store: String, authorId: String, quantity: Int, unit: String, category: String, notes: String): Result<Unit> = Result.success(Unit)

    var addedItems: List<ShoppingItem> = emptyList()
    override suspend fun addShoppingItems(householdId: String, items: List<ShoppingItem>): Result<Unit> {
        addedItems = addedItems + items
        return Result.success(Unit)
    }

    override fun observeCustomStores(householdId: String): Flow<List<String>> = flowOf(emptyList())

    override suspend fun addCustomStore(householdId: String, storeName: String): Result<Unit> = Result.success(Unit)

    override suspend fun toggleShoppingItem(householdId: String, itemId: String, isChecked: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun deleteShoppingItem(householdId: String, itemId: String): Result<Unit> = Result.success(Unit)

    var archivedWithPantry: Boolean? = null
    override suspend fun archiveShoppingList(householdId: String, stockPantry: Boolean): Result<Unit> {
        archivedWithPantry = stockPantry
        return Result.success(Unit)
    }

    override fun observeShoppingHistory(householdId: String): Flow<List<ShoppingHistory>> = flowOf(emptyList())

    override suspend fun bulkToggleItems(householdId: String, itemIds: List<String>, isChecked: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun deleteCheckedItems(householdId: String): Result<Unit> = Result.success(Unit)

    override suspend fun restoreHistory(householdId: String, historyId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFrequentItems(householdId: String, limit: Int): Result<List<String>> = Result.success(emptyList())

    fun reset() {
        stubItems = emptyList()
        addedItems = emptyList()
        archivedWithPantry = null
    }
}
