package com.monsteraltech.habitly.feature.shopping.presentation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.usecase.*
import com.monsteraltech.habitly.feature.shopping.domain.util.ProductNameNormalizer
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pestañas de la pantalla: la lista de la compra y lo que ya hay en casa. */
enum class ShoppingTab { LIST, PANTRY }

data class ShoppingUiState(
    val allItems: List<ShoppingItem> = emptyList(),
    val selectedTab: ShoppingTab = ShoppingTab.LIST,
    val pantryItems: List<PantryItem> = emptyList(),
    val availableStores: List<String> = listOf("Mercadona", "Lidl", "Carrefour", "Cualquiera"),
    val selectedStore: String = "Cualquiera",
    val isLoading: Boolean = true,
    val error: String? = null,
    @StringRes val errorRes: Int? = null,
    val recentlyDeletedName: String? = null,
    val showCompletedSection: Boolean = false,
    val frequentItems: List<String> = emptyList(),
    val isLoadingFrequent: Boolean = false
) {
    val pendingItems: List<ShoppingItem>
        get() = allItems.filter { !it.isChecked }
    
    val completedItems: List<ShoppingItem>
        get() = allItems.filter { it.isChecked }
    
    val filteredPendingItems: List<ShoppingItem>
        get() = if (selectedStore == "Cualquiera") pendingItems else pendingItems.filter { it.store == selectedStore }
    
    val filteredCompletedItems: List<ShoppingItem>
        get() = if (selectedStore == "Cualquiera") completedItems else completedItems.filter { it.store == selectedStore }
    
    val pendingItemsByStore: Map<String, List<ShoppingItem>>
        get() = filteredPendingItems.groupBy { it.store }.toSortedMap()
    
    val completedItemsByStore: Map<String, List<ShoppingItem>>
        get() = filteredCompletedItems.groupBy { it.store }.toSortedMap()
    
    val totalItems: Int
        get() = allItems.size
    
    val checkedCount: Int
        get() = completedItems.size
    
    val progress: Float
        get() = if (totalItems == 0) 0f else checkedCount.toFloat() / totalItems.toFloat()

    val pantryByCategory: Map<String, List<PantryItem>>
        get() = pantryItems.groupBy { it.category.ifBlank { OTHER_CATEGORY } }.toSortedMap()

    /** Cuánto hay en casa de un producto, buscándolo por nombre normalizado. */
    fun pantryQuantityOf(name: String): Int? {
        val id = ProductNameNormalizer.toDocumentId(name) ?: return null
        return pantryItems.find { it.id == id }?.quantity
    }

    private companion object {
        const val OTHER_CATEGORY = "Otros"
    }
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val observeShoppingListUseCase: ObserveShoppingListUseCase,
    private val addShoppingItemUseCase: AddShoppingItemUseCase,
    private val toggleShoppingItemUseCase: ToggleShoppingItemUseCase,
    private val deleteShoppingItemUseCase: DeleteShoppingItemUseCase,
    private val archiveShoppingListUseCase: ArchiveShoppingListUseCase,
    private val observeCustomStoresUseCase: ObserveCustomStoresUseCase,
    private val addCustomStoreUseCase: AddCustomStoreUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val checkAllItemsUseCase: CheckAllItemsUseCase,
    private val deleteCheckedItemsUseCase: DeleteCheckedItemsUseCase,
    private val getFrequentItemsUseCase: GetFrequentItemsUseCase,
    private val observePantryUseCase: ObservePantryUseCase,
    private val adjustPantryQuantityUseCase: AdjustPantryQuantityUseCase,
    private val deletePantryItemUseCase: DeletePantryItemUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var currentHouseholdId: String? = null
    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"
        
    private var observeListJob: Job? = null
    private var observeStoresJob: Job? = null
    private var observePantryJob: Job? = null

    // Último producto borrado, guardado en memoria para poder deshacer (re-añadir).
    private var lastDeletedItem: ShoppingItem? = null

    init {
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId)
                .mapNotNull { it?.activeHouseholdId }
                .distinctUntilChanged()
                .collectLatest { householdId ->
                    currentHouseholdId = householdId
                    startObserving(householdId)
                    loadFrequentItems(householdId)
                }
        }
    }

    private fun startObserving(householdId: String) {
        observeListJob?.cancel()
        observeStoresJob?.cancel()
        observePantryJob?.cancel()

        observePantryJob = viewModelScope.launch {
            observePantryUseCase(householdId)
                .catch { /* la despensa es secundaria: no debe tumbar la pantalla */ }
                .collect { items -> _uiState.update { it.copy(pantryItems = items) } }
        }

        observeStoresJob = viewModelScope.launch {
            observeCustomStoresUseCase(householdId)
                .catch { /* ignorar errores silenciosos aquí por ahora */ }
                .collect { customStores ->
                    val defaultStores = listOf("Mercadona", "Lidl", "Carrefour", "Cualquiera")
                    val combined = (defaultStores + customStores).distinct()
                    _uiState.update { it.copy(availableStores = combined) }
                }
        }

        observeListJob = viewModelScope.launch {
            observeShoppingListUseCase(householdId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { items ->
                    _uiState.update { it.copy(allItems = items, isLoading = false, error = null) }
                }
        }
    }

    private fun loadFrequentItems(householdId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFrequent = true) }
            val result = getFrequentItemsUseCase(householdId, 8)
            _uiState.update { 
                it.copy(
                    frequentItems = result.getOrDefault(emptyList()),
                    isLoadingFrequent = false
                ) 
            }
        }
    }

    fun onSelectStore(store: String) {
        _uiState.update { it.copy(selectedStore = store) }
    }

    fun onAddCustomStore(storeName: String) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            val result = addCustomStoreUseCase(householdId, storeName)
            if (result.isSuccess) {
                onSelectStore(storeName)
            } else {
                Log.e("ShoppingViewModel", "Error adding custom store", result.exceptionOrNull())
                _uiState.update { it.copy(errorRes = R.string.shopping_error_store) }
            }
        }
    }

    fun onAddItem(name: String, quantity: Int = 1, unit: String = "unidad", category: String = "", notes: String = "") {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            addShoppingItemUseCase(householdId, name, _uiState.value.selectedStore, currentUserId, quantity, unit, category, notes)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_add) } }
        }
    }

    fun onToggleItem(itemId: String, isChecked: Boolean) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            toggleShoppingItemUseCase(householdId, itemId, isChecked)
                .onFailure {
                    Log.e("ShoppingViewModel", "Error toggling item", it)
                    _uiState.update { it.copy(errorRes = R.string.shopping_error_update) }
                }
        }
    }

    fun onDeleteItem(itemId: String) {
        val householdId = currentHouseholdId ?: return
        val item = _uiState.value.allItems.find { it.id == itemId }
        viewModelScope.launch {
            deleteShoppingItemUseCase(householdId, itemId)
                .onSuccess {
                    if (item != null) {
                        lastDeletedItem = item
                        _uiState.update { it.copy(recentlyDeletedName = item.name) }
                    }
                }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_delete) } }
        }
    }

    fun onUndoDelete() {
        val householdId = currentHouseholdId ?: return
        val item = lastDeletedItem ?: return
        lastDeletedItem = null
        _uiState.update { it.copy(recentlyDeletedName = null) }
        viewModelScope.launch {
            addShoppingItemUseCase(
                householdId, item.name, item.store, item.authorId,
                item.quantity, item.unit, item.category, item.notes
            ).onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_add) } }
        }
    }

    fun onUndoSnackbarShown() {
        lastDeletedItem = null
        _uiState.update { it.copy(recentlyDeletedName = null) }
    }

    fun onSelectTab(tab: ShoppingTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onAdjustPantryQuantity(itemId: String, delta: Int) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            adjustPantryQuantityUseCase(householdId, itemId, delta)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_update) } }
        }
    }

    fun onDeletePantryItem(itemId: String) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            deletePantryItemUseCase(householdId, itemId)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_delete) } }
        }
    }

    fun onArchiveList(stockPantry: Boolean = true) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = archiveShoppingListUseCase(householdId, stockPantry)

            _uiState.update { it.copy(isLoading = false) }

            if (result.isFailure) {
                _uiState.update { it.copy(errorRes = R.string.shopping_error_archive) }
                Log.e("ShoppingViewModel", "Archive failed", result.exceptionOrNull())
            }
        }
    }

    fun onCheckAll() {
        val householdId = currentHouseholdId ?: return
        val pendingIds = _uiState.value.filteredPendingItems.map { it.id }
        if (pendingIds.isEmpty()) return

        viewModelScope.launch {
            checkAllItemsUseCase(householdId, pendingIds, true)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_update) } }
        }
    }

    fun onDeleteChecked() {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            deleteCheckedItemsUseCase(householdId)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_delete) } }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorRes = null, error = null) }
    }

    fun onToggleCompletedSection() {
        _uiState.update { it.copy(showCompletedSection = !it.showCompletedSection) }
    }

    fun onQuickAdd(itemName: String) {
        onAddItem(itemName)
    }
}
