package com.monsteraltech.habitly.feature.shopping.domain.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {
    fun observeShoppingList(householdId: String): Flow<List<ShoppingItem>>

    suspend fun addShoppingItem(householdId: String, name: String, store: String, authorId: String, quantity: Int = 1, unit: String = "unidad", category: String = "", notes: String = ""): Result<Unit>

    /**
     * Adds several items in a single atomic batch. The repository assigns id and createdAt and
     * forces isChecked = false, so callers cannot add an item that is already ticked off.
     */
    suspend fun addShoppingItems(householdId: String, items: List<ShoppingItem>): Result<Unit>

    fun observeCustomStores(householdId: String): Flow<List<String>>

    suspend fun addCustomStore(householdId: String, storeName: String): Result<Unit>

    suspend fun toggleShoppingItem(householdId: String, itemId: String, isChecked: Boolean): Result<Unit>

    suspend fun deleteShoppingItem(householdId: String, itemId: String): Result<Unit>

    /**
     * Files the current list into the history and clears the active one.
     *
     * @param stockPantry when true, the items ticked off as bought also move into the pantry,
     *   inside the same atomic batch.
     */
    suspend fun archiveShoppingList(householdId: String, stockPantry: Boolean = true): Result<Unit>

    fun observeShoppingHistory(householdId: String): Flow<List<ShoppingHistory>>

    suspend fun bulkToggleItems(householdId: String, itemIds: List<String>, isChecked: Boolean): Result<Unit>

    suspend fun deleteCheckedItems(householdId: String): Result<Unit>

    suspend fun restoreHistory(householdId: String, historyId: String): Result<Unit>

    /** The most frequently bought item names across the history, for the quick-add suggestions. */
    suspend fun getFrequentItems(householdId: String, limit: Int = 10): Result<List<String>>
}
