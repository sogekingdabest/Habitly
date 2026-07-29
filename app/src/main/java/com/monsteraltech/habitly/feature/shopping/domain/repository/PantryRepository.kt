package com.monsteraltech.habitly.feature.shopping.domain.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import kotlinx.coroutines.flow.Flow

interface PantryRepository {

    /** Observes what is at home in real time. */
    fun observePantry(householdId: String): Flow<List<PantryItem>>

    /**
     * Puts products into the pantry, adding onto whatever was already there. Each document id is
     * the normalised name, so repeating a product updates its entry instead of duplicating it.
     */
    suspend fun upsertItems(householdId: String, items: List<PantryItem>): Result<Unit>

    /** Adds [delta] to the quantity. At zero or below, the product leaves the pantry. */
    suspend fun adjustQuantity(householdId: String, itemId: String, delta: Int): Result<Unit>

    suspend fun deleteItem(householdId: String, itemId: String): Result<Unit>
}
