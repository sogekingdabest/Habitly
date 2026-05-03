package com.monsteraltech.habitly.feature.shopping.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.usecase.*
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

data class ShoppingUiState(
    val allItems: List<ShoppingItem> = emptyList(),
    val availableStores: List<String> = listOf("Mercadona", "Lidl", "Carrefour", "Cualquiera"),
    val selectedStore: String = "Cualquiera",
    val isLoading: Boolean = true,
    val error: String? = null,
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
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var currentHouseholdId: String? = null
    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"
        
    private var observeListJob: Job? = null
    private var observeStoresJob: Job? = null

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
            }
        }
    }

    fun onAddItem(name: String, quantity: Int = 1, unit: String = "unidad", category: String = "", notes: String = "") {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            addShoppingItemUseCase(householdId, name, _uiState.value.selectedStore, currentUserId, quantity, unit, category, notes)
        }
    }

    fun onToggleItem(itemId: String, isChecked: Boolean) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            val result = toggleShoppingItemUseCase(householdId, itemId, isChecked)
            if (result.isFailure) {
                Log.e("ShoppingViewModel", "Error toggling item: ${result.exceptionOrNull()}")
            }
        }
    }

    fun onDeleteItem(itemId: String) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            deleteShoppingItemUseCase(householdId, itemId)
        }
    }

    fun onArchiveList() {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = archiveShoppingListUseCase(householdId)
            
            _uiState.update { it.copy(isLoading = false) }
            
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
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
        }
    }

    fun onDeleteChecked() {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            deleteCheckedItemsUseCase(householdId)
        }
    }

    fun onToggleCompletedSection() {
        _uiState.update { it.copy(showCompletedSection = !it.showCompletedSection) }
    }

    fun onQuickAdd(itemName: String) {
        onAddItem(itemName)
    }
}
