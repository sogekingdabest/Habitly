package com.monsteraltech.habitly.feature.shopping.domain.repository

import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {
    /**
     * Observa en tiempo real la lista de la compra de una casa específica.
     * @param householdId ID del grupo de la casa.
     */
    fun observeShoppingList(householdId: String): Flow<List<ShoppingItem>>

    /**
     * Añade un nuevo ítem a la lista de la compra.
     */
    suspend fun addShoppingItem(householdId: String, name: String, store: String, authorId: String): Result<Unit>

    /**
     * Observa en tiempo real los supermercados personalizados de una casa.
     */
    fun observeCustomStores(householdId: String): Flow<List<String>>

    /**
     * Añade un nuevo supermercado personalizado.
     */
    suspend fun addCustomStore(householdId: String, storeName: String): Result<Unit>

    /**
     * Cambia el estado de (marcado/desmarcado) de un ítem.
     */
    suspend fun toggleShoppingItem(householdId: String, itemId: String, isChecked: Boolean): Result<Unit>
    
    /**
     * Borra un ítem individual de la lista.
     */
    suspend fun deleteShoppingItem(householdId: String, itemId: String): Result<Unit>

    /**
     * Guarda la lista actual en el histórico y elimina los ítems de la lista activa.
     */
    suspend fun archiveShoppingList(householdId: String): Result<Unit>
    
    fun observeShoppingHistory(householdId: String): Flow<List<ShoppingHistory>>
}
