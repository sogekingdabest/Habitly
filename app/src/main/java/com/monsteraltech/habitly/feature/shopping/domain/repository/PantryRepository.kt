package com.monsteraltech.habitly.feature.shopping.domain.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import kotlinx.coroutines.flow.Flow

interface PantryRepository {

    /** Observa en tiempo real lo que hay en casa. */
    fun observePantry(householdId: String): Flow<List<PantryItem>>

    /**
     * Mete productos en la despensa sumándolos a lo que ya hubiera.
     * El id de cada documento es el nombre normalizado, así que repetir un producto
     * actualiza su entrada en vez de duplicarla.
     */
    suspend fun upsertItems(householdId: String, items: List<PantryItem>): Result<Unit>

    /** Suma [delta] a la cantidad. Si llega a cero o menos, el producto sale de la despensa. */
    suspend fun adjustQuantity(householdId: String, itemId: String, delta: Int): Result<Unit>

    suspend fun deleteItem(householdId: String, itemId: String): Result<Unit>
}
