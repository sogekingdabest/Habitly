package com.monsteraltech.habitly.feature.shopping.domain.usecase

import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingHistory
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveShoppingListUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    operator fun invoke(householdId: String): Flow<List<ShoppingItem>> {
        return repository.observeShoppingList(householdId)
    }
}

class AddShoppingItemUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, name: String, store: String, authorId: String, quantity: Int = 1, unit: String = "unidad", category: String = "", notes: String = ""): Result<Unit> {
        if (name.isBlank()) return Result.failure(Exception("El nombre no puede estar vacío"))
        return repository.addShoppingItem(householdId, name, store, authorId, quantity, unit, category, notes)
    }
}

/**
 * Alta de varios productos de golpe (dictado por voz), en un único batch: tres escrituras
 * sueltas serían tres repintados de la lista y tres refrescos del widget.
 *
 * @return cuántos se han dado de alta.
 */
class AddShoppingItemsUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(
        householdId: String,
        store: String,
        authorId: String,
        products: List<ShoppingItem>
    ): Result<Int> {
        val valid = products.filter { it.name.isNotBlank() }
        if (valid.isEmpty()) return Result.success(0)

        val items = valid.map { it.copy(store = store, authorId = authorId) }
        return repository.addShoppingItems(householdId, items).map { valid.size }
    }
}

class ToggleShoppingItemUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, itemId: String, isChecked: Boolean): Result<Unit> {
        return repository.toggleShoppingItem(householdId, itemId, isChecked)
    }
}

class DeleteShoppingItemUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, itemId: String): Result<Unit> {
        return repository.deleteShoppingItem(householdId, itemId)
    }
}

class ArchiveShoppingListUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    /** @param stockPantry si lo comprado debe guardarse además en la despensa. */
    suspend operator fun invoke(householdId: String, stockPantry: Boolean = true): Result<Unit> {
        return repository.archiveShoppingList(householdId, stockPantry)
    }
}

class ObserveShoppingHistoryUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    operator fun invoke(householdId: String): Flow<List<ShoppingHistory>> {
        return repository.observeShoppingHistory(householdId)
    }
}

class ObserveCustomStoresUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    operator fun invoke(householdId: String): Flow<List<String>> {
        return repository.observeCustomStores(householdId)
    }
}

class AddCustomStoreUseCase @Inject constructor(
    private val repository: ShoppingRepository
) {
    suspend operator fun invoke(householdId: String, storeName: String): Result<Unit> {
        if (storeName.isBlank()) return Result.failure(Exception("El nombre del supermercado no puede estar vacío"))
        return repository.addCustomStore(householdId, storeName)
    }
}
