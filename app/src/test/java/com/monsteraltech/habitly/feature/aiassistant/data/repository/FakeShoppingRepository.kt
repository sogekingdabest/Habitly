package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeShoppingRepository : ShoppingRepository {

    var stubItems: List<ShoppingItem> = emptyList()

    override fun observeShoppingList(householdId: String): Flow<List<ShoppingItem>> = flowOf(stubItems)

    override suspend fun addShoppingItem(householdId: String, name: String, store: String, authorId: String): Result<Unit> = Result.success(Unit)

    override fun observeCustomStores(householdId: String): Flow<List<String>> = flowOf(emptyList())

    override suspend fun addCustomStore(householdId: String, storeName: String): Result<Unit> = Result.success(Unit)

    override suspend fun toggleShoppingItem(householdId: String, itemId: String, isChecked: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun deleteShoppingItem(householdId: String, itemId: String): Result<Unit> = Result.success(Unit)

    override suspend fun archiveShoppingList(householdId: String): Result<Unit> = Result.success(Unit)

    override fun observeShoppingHistory(householdId: String): Flow<List<ShoppingHistory>> = flowOf(emptyList())

    fun reset() {
        stubItems = emptyList()
    }
}
