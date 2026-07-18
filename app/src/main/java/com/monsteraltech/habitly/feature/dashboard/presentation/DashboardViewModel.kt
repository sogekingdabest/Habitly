package com.monsteraltech.habitly.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.usecase.ObserveRoutinesUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ToggleRoutineUseCase
import com.monsteraltech.habitly.feature.routines.presentation.RoutinesViewModel
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.domain.usecase.ObserveShoppingListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val household: Household? = null,
    val pendingShoppingItems: List<ShoppingItem> = emptyList(),
    val pendingRoutines: List<Routine> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val observeShoppingListUseCase: ObserveShoppingListUseCase,
    private val observeRoutinesUseCase: ObserveRoutinesUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    // Última rutina completada desde el dashboard, para ofrecer "Deshacer" en un snackbar.
    private val _recentlyCompleted = MutableStateFlow<Routine?>(null)
    val recentlyCompleted: StateFlow<Routine?> = _recentlyCompleted.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = observeUserProfileUseCase(currentUserId)
        .mapNotNull { it?.activeHouseholdId }
        .flatMapLatest { householdId ->
            if (householdId.isBlank()) return@flatMapLatest flowOf(DashboardUiState(isLoading = false))

            val householdFlow = observeHouseholdUseCase(householdId)
            val shoppingFlow = observeShoppingListUseCase(householdId)
            val routinesFlow = observeRoutinesUseCase(currentUserId, householdId)

            combine(householdFlow, shoppingFlow, routinesFlow) { household, shoppingList, routines ->
                val pendingShopping = shoppingList.filter { !it.isChecked }
                val pendingRoutines = routines.filter { !RoutinesViewModel.isRoutineCompletedToday(it) }
                
                DashboardUiState(
                    isLoading = false,
                    household = household,
                    pendingShoppingItems = pendingShopping,
                    pendingRoutines = pendingRoutines
                )
            }.catch { e ->
                emit(DashboardUiState(isLoading = false, error = e.message))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState()
        )

    fun onToggleRoutine(routine: Routine) {
        val state = uiState.value
        val householdId = state.household?.id ?: return
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            // Desde el dashboard solo mostramos pendientes, así que siempre estamos completando
            toggleRoutineUseCase(currentUserId, householdId, routine, true)
                .onSuccess { _recentlyCompleted.value = routine }
        }
    }

    fun onUndoComplete() {
        val routine = _recentlyCompleted.value ?: return
        val householdId = uiState.value.household?.id ?: return
        _recentlyCompleted.value = null
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            toggleRoutineUseCase(currentUserId, householdId, routine, false)
        }
    }

    fun onUndoShown() {
        _recentlyCompleted.value = null
    }
}
