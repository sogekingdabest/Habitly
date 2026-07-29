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
import com.monsteraltech.habitly.feature.shopping.domain.util.PlainListParser
import com.monsteraltech.habitly.feature.shopping.domain.util.ProductNameNormalizer
import com.monsteraltech.habitly.feature.shopping.presentation.components.DEFAULT_UNIT
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

/** The screen's tabs: the shopping list, and what is already at home. */
enum class ShoppingTab { LIST, PANTRY }

/** Wildcard store: filters nothing, and is the default for a new product. */
const val ANY_STORE = "Cualquiera"

/** Supermarkets the app ships with, ahead of whatever each household adds. */
val DEFAULT_STORES = listOf("Mercadona", "Lidl", "Carrefour", ANY_STORE)

/**
 * State of the quick-add sheet.
 *
 * A product only needs a name; the remaining fields live folded away behind "More options" with the
 * same defaults the full screen used to have.
 */
data class QuickAddState(
    val isOpen: Boolean = false,
    val name: String = "",
    val quantity: Int = 1,
    val unit: String = DEFAULT_UNIT,
    val store: String = ANY_STORE,
    val category: String = "",
    val notes: String = "",
    val showMoreOptions: Boolean = false,
    val isSaving: Boolean = false,
    /** How many have been added without closing the sheet. */
    val savedCount: Int = 0
) {
    val canSave: Boolean
        get() = name.isNotBlank() && !isSaving
}

data class ShoppingUiState(
    val allItems: List<ShoppingItem> = emptyList(),
    val selectedTab: ShoppingTab = ShoppingTab.LIST,
    val pantryItems: List<PantryItem> = emptyList(),
    val availableStores: List<String> = DEFAULT_STORES,
    val selectedStore: String = ANY_STORE,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    @StringRes val errorRes: Int? = null,
    val recentlyDeletedName: String? = null,
    /** Product just taken out of the pantry, so the gesture can offer "Undo". */
    val recentlyDeletedPantryName: String? = null,
    val showCompletedSection: Boolean = false,
    val frequentItems: List<String> = emptyList(),
    val isLoadingFrequent: Boolean = false,
    val quickAdd: QuickAddState = QuickAddState(),
    /** Products added by voice in the last batch, to confirm with a snackbar. */
    val voiceAddedCount: Int? = null
) {
    val pendingItems: List<ShoppingItem>
        get() = allItems.filter { !it.isChecked }

    val completedItems: List<ShoppingItem>
        get() = allItems.filter { it.isChecked }

    val isSearching: Boolean
        get() = searchQuery.isNotBlank()

    val filteredPendingItems: List<ShoppingItem>
        get() = pendingItems.applyFilters()

    val filteredCompletedItems: List<ShoppingItem>
        get() = completedItems.applyFilters()

    val pendingItemsByStore: Map<String, List<ShoppingItem>>
        get() = filteredPendingItems.groupBy { it.store }.toSortedMap()

    val completedItemsByStore: Map<String, List<ShoppingItem>>
        get() = filteredCompletedItems.groupBy { it.store }.toSortedMap()

    /**
     * With a search active the completed section unfolds itself: keeping it hidden is exactly what
     * makes people re-add products they have already bought.
     */
    val isCompletedSectionExpanded: Boolean
        get() = showCompletedSection || isSearching

    val totalItems: Int
        get() = allItems.size

    val checkedCount: Int
        get() = completedItems.size

    val progress: Float
        get() = if (totalItems == 0) 0f else checkedCount.toFloat() / totalItems.toFloat()

    val pantryByCategory: Map<String, List<PantryItem>>
        get() = pantryItems.groupBy { it.category.ifBlank { OTHER_CATEGORY } }.toSortedMap()

    /** How much of what is being typed into quick-add is already at home. */
    val quickAddPantryMatch: PantryItem?
        get() {
            val id = ProductNameNormalizer.toDocumentId(quickAdd.name) ?: return null
            return pantryItems.find { it.id == id }
        }

    /** A product already on the list under that same name, pending or bought. */
    val quickAddDuplicate: ShoppingItem?
        get() = allItems.find { ProductNameNormalizer.isSameProduct(it.name, quickAdd.name) }

    /** How much of a product is at home, looked up by normalised name. */
    fun pantryQuantityOf(name: String): Int? {
        val id = ProductNameNormalizer.toDocumentId(name) ?: return null
        return pantryItems.find { it.id == id }?.quantity
    }

    /**
     * Store and search. Search deliberately overrides the store filter: if you ask "is the rice on
     * the list?" you want to know wherever it is, not only in the selected store.
     */
    private fun List<ShoppingItem>.applyFilters(): List<ShoppingItem> = when {
        isSearching -> {
            val needle = ProductNameNormalizer.normalize(searchQuery)
            filter { ProductNameNormalizer.normalize(it.name).contains(needle) }
        }

        selectedStore == ANY_STORE -> this
        else -> filter { it.store == selectedStore }
    }

    private companion object {
        const val OTHER_CATEGORY = "Otros"
    }
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val observeShoppingListUseCase: ObserveShoppingListUseCase,
    private val addShoppingItemUseCase: AddShoppingItemUseCase,
    private val addShoppingItemsUseCase: AddShoppingItemsUseCase,
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
    private val restorePantryItemUseCase: RestorePantryItemUseCase,
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

    // Last deleted product, held in memory so it can be undone by re-adding it.
    private var lastDeletedItem: ShoppingItem? = null

    // Same for the pantry: taking a product out is undoable too.
    private var lastDeletedPantryItem: PantryItem? = null

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
                    val combined = (DEFAULT_STORES + customStores).distinct()
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

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onClearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun onAddItem(name: String, quantity: Int = 1, unit: String = DEFAULT_UNIT, category: String = "", notes: String = "") {
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
        val item = _uiState.value.pantryItems.find { it.id == itemId }
        viewModelScope.launch {
            deletePantryItemUseCase(householdId, itemId)
                .onSuccess {
                    if (item != null) {
                        lastDeletedPantryItem = item
                        _uiState.update { it.copy(recentlyDeletedPantryName = item.name) }
                    }
                }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_delete) } }
        }
    }

    fun onUndoDeletePantry() {
        val householdId = currentHouseholdId ?: return
        val item = lastDeletedPantryItem ?: return
        lastDeletedPantryItem = null
        _uiState.update { it.copy(recentlyDeletedPantryName = null) }
        viewModelScope.launch {
            restorePantryItemUseCase(householdId, item)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_update) } }
        }
    }

    fun onUndoPantrySnackbarShown() {
        lastDeletedPantryItem = null
        _uiState.update { it.copy(recentlyDeletedPantryName = null) }
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

    // ---------- Add by voice ----------

    /**
     * Adding from the header's dictation: "leche, huevos y pan" is three products.
     *
     * The text goes through [PlainListParser], which resolves quantity and unit ("dos litros de
     * leche" → 2 L) instantly. It **deliberately skips the local model**: loading a gigabyte-sized
     * model and waiting on inference for three words said in the kitchen is precisely the problem
     * this function exists to solve.
     */
    fun onVoiceProducts(spokenText: String) {
        val householdId = currentHouseholdId ?: return
        val products = PlainListParser.fromSpeech(spokenText)
        if (products.isEmpty()) {
            _uiState.update { it.copy(errorRes = R.string.shopping_voice_not_understood) }
            return
        }

        viewModelScope.launch {
            addShoppingItemsUseCase(
                householdId = householdId,
                store = _uiState.value.selectedStore,
                authorId = currentUserId,
                products = products.map { product ->
                    ShoppingItem(
                        name = product.name,
                        quantity = product.quantity,
                        unit = product.unit
                    )
                }
            )
                .onSuccess { added -> _uiState.update { it.copy(voiceAddedCount = added) } }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.shopping_error_add) } }
        }
    }

    fun onVoiceAddedShown() {
        _uiState.update { it.copy(voiceAddedCount = null) }
    }

    /**
     * Dictation inside the quick-add sheet: it fills the form rather than saving. The user is
     * looking at the form here, so the natural thing is for them to see the recognised quantity and
     * unit and press the button themselves.
     */
    fun onQuickAddVoice(spokenText: String) {
        val product = PlainListParser.fromSpeech(spokenText, limit = 1).firstOrNull()
        if (product == null) {
            _uiState.update { it.copy(errorRes = R.string.shopping_voice_not_understood) }
            return
        }
        updateQuickAdd {
            it.copy(name = product.name, quantity = product.quantity, unit = product.unit)
        }
    }

    // ---------- Quick add (bottom sheet) ----------

    /** Opens the sheet inheriting whichever store is filtered, which is almost always the right one. */
    fun onOpenQuickAdd() {
        val store = _uiState.value.selectedStore
        _uiState.update { it.copy(quickAdd = QuickAddState(isOpen = true, store = store)) }
    }

    fun onDismissQuickAdd() {
        _uiState.update { it.copy(quickAdd = QuickAddState()) }
    }

    fun onQuickAddNameChange(name: String) = updateQuickAdd { it.copy(name = name) }

    fun onQuickAddQuantityChange(quantity: Int) =
        updateQuickAdd { it.copy(quantity = quantity.coerceAtLeast(1)) }

    fun onQuickAddUnitChange(unit: String) = updateQuickAdd { it.copy(unit = unit) }

    fun onQuickAddStoreChange(store: String) = updateQuickAdd { it.copy(store = store) }

    fun onQuickAddCategoryChange(category: String) = updateQuickAdd { it.copy(category = category) }

    fun onQuickAddNotesChange(notes: String) = updateQuickAdd { it.copy(notes = notes) }

    fun onToggleQuickAddOptions() = updateQuickAdd { it.copy(showMoreOptions = !it.showMoreOptions) }

    /**
     * Saves and **leaves the sheet open**: it clears the name and keeps the other options. Jotting
     * down ten things in a row used to mean ten round trips to a full screen, with the keyboard
     * opening and closing each time.
     */
    fun onQuickAddSave() {
        val householdId = currentHouseholdId ?: return
        val state = _uiState.value.quickAdd
        if (!state.canSave) return

        updateQuickAdd { it.copy(isSaving = true) }
        viewModelScope.launch {
            addShoppingItemUseCase(
                householdId = householdId,
                name = state.name.trim(),
                store = state.store,
                authorId = currentUserId,
                quantity = state.quantity,
                unit = state.unit,
                category = state.category,
                notes = state.notes
            )
                .onSuccess {
                    updateQuickAdd {
                        it.copy(
                            name = "",
                            quantity = 1,
                            notes = "",
                            isSaving = false,
                            savedCount = it.savedCount + 1
                        )
                    }
                }
                .onFailure {
                    updateQuickAdd { current -> current.copy(isSaving = false) }
                    _uiState.update { current -> current.copy(errorRes = R.string.shopping_error_add) }
                }
        }
    }

    private inline fun updateQuickAdd(transform: (QuickAddState) -> QuickAddState) {
        _uiState.update { it.copy(quickAdd = transform(it.quickAdd)) }
    }
}
