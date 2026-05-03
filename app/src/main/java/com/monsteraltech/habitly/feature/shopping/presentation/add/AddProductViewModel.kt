package com.monsteraltech.habitly.feature.shopping.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.shopping.domain.usecase.AddShoppingItemUseCase
import com.monsteraltech.habitly.feature.shopping.domain.usecase.ObserveCustomStoresUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class AddProductUiState(
    val name: String = "",
    val quantity: Int = 1,
    val unit: String = "unidad",
    val store: String = "Cualquiera",
    val category: String = "",
    val notes: String = "",
    val availableStores: List<String> = listOf("Mercadona", "Lidl", "Carrefour", "Cualquiera"),
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val addShoppingItemUseCase: AddShoppingItemUseCase,
    private val observeCustomStoresUseCase: ObserveCustomStoresUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProductUiState())
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

    private var currentHouseholdId: String? = null
    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"

    init {
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId)
                .mapNotNull { it?.activeHouseholdId }
                .distinctUntilChanged()
                .collectLatest { householdId ->
                    currentHouseholdId = householdId
                    observeStores(householdId)
                }
        }
    }

    private fun observeStores(householdId: String) {
        viewModelScope.launch {
            observeCustomStoresUseCase(householdId)
                .catch { /* ignorar errores silenciosos */ }
                .collect { customStores ->
                    val defaultStores = listOf("Mercadona", "Lidl", "Carrefour", "Cualquiera")
                    val combined = (defaultStores + customStores).distinct()
                    _uiState.update { it.copy(availableStores = combined) }
                }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onQuantityChange(quantity: Int) {
        _uiState.update { it.copy(quantity = quantity.coerceAtLeast(1)) }
    }

    fun onUnitChange(unit: String) {
        _uiState.update { it.copy(unit = unit) }
    }

    fun onStoreChange(store: String) {
        _uiState.update { it.copy(store = store) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onSaveProduct() {
        val householdId = currentHouseholdId ?: return
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "El nombre del producto no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = addShoppingItemUseCase(
                householdId = householdId,
                name = state.name,
                store = state.store,
                authorId = currentUserId,
                quantity = state.quantity,
                unit = state.unit,
                category = state.category,
                notes = state.notes
            )
            
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false, success = true) }
            } else {
                _uiState.update { 
                    it.copy(
                        isSaving = false, 
                        error = result.exceptionOrNull()?.message ?: "Error al guardar el producto"
                    ) 
                }
            }
        }
    }

    fun onResetSuccess() {
        _uiState.update { it.copy(success = false) }
    }
}
