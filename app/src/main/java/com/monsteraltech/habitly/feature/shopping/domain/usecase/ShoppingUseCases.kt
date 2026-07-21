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
