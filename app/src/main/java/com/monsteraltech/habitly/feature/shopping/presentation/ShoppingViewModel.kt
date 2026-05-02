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
    val itemsByStore: Map<String, List<ShoppingItem>> = emptyMap(),
    val availableStores: List<String> = listOf("Mercadona", "Lidl", "Carrefour", "Cualquiera"),
    val selectedStore: String = "Cualquiera",
    val isLoading: Boolean = true,
    val error: String? = null
)

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
                    val grouped = items.groupBy { it.store }
                        .toSortedMap()
                        .mapValues { entry -> 
                            entry.value.sortedBy { it.isChecked } 
                        }
                    _uiState.update { it.copy(itemsByStore = grouped, isLoading = false, error = null) }
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

    fun onAddItem(name: String) {
        val householdId = currentHouseholdId ?: return
        viewModelScope.launch {
            addShoppingItemUseCase(householdId, name, _uiState.value.selectedStore, currentUserId)
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
}
