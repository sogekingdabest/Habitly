package com.monsteraltech.habitly.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.dashboard.data.ConnectivityObserver
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.ObserveRoutinesUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ToggleRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
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
import java.time.LocalDate
import javax.inject.Inject

/**
 * Cuántas rutinas de casa lleva hechas hoy un miembro. [name] llega vacío si su perfil aún
 * no está en `Household.memberProfiles` (casa anterior al campo, o compañero que todavía no
 * ha abierto la app); la UI pone entonces el texto de reserva.
 */
data class MemberTally(val memberId: String, val name: String, val count: Int)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val household: Household? = null,
    val pendingShoppingItems: List<ShoppingItem> = emptyList(),
    val pendingRoutines: List<Routine> = emptyList(),
    /** Rutinas que cuentan para hoy: las que tocaban más las que se han hecho. */
    val todayRoutinesTotal: Int = 0,
    val todayRoutinesDone: Int = 0,
    /** Reparto del día entre miembros, de más a menos. */
    val todayByMember: List<MemberTally> = emptyList(),
    /** Uid actual, para marcar con "Te toca" las rutinas de casa asignadas a este usuario. */
    val currentUserId: String = "",
    /** Sin red: lo que se marque queda guardado en el móvil y subirá al volver la conexión. */
    val isOffline: Boolean = false,
    val error: String? = null
) {
    val todayProgress: Float
        get() = if (todayRoutinesTotal == 0) 0f
        else todayRoutinesDone.toFloat() / todayRoutinesTotal.toFloat()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val observeShoppingListUseCase: ObserveShoppingListUseCase,
    private val observeRoutinesUseCase: ObserveRoutinesUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private val _recentlyCompleted = MutableStateFlow<Routine?>(null)
    val recentlyCompleted: StateFlow<Routine?> = _recentlyCompleted.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = observeUserProfileUseCase(currentUserId)
        .mapNotNull { it?.activeHouseholdId }
        .flatMapLatest { householdId ->
            if (householdId.isBlank()) return@flatMapLatest flowOf(DashboardUiState(isLoading = false))

            val householdFlow = observeHouseholdUseCase(householdId)
            val shoppingFlow = observeShoppingListUseCase(householdId)
            val routinesFlow = observeRoutinesUseCase(currentUserId, householdId)

            combine(
                householdFlow,
                shoppingFlow,
                routinesFlow,
                connectivityObserver.isOnline
            ) { household, shoppingList, routines, isOnline ->
                val today = LocalDate.now()
                val pendingShopping = shoppingList.filter { !it.isChecked }

                val pendingRoutines = routines.filter { RoutineSchedule.isPendingOn(it, today) }
                val completedToday = routines.filter { RoutineSchedule.isCompletedOn(it, today) }

                DashboardUiState(
                    isLoading = false,
                    household = household,
                    pendingShoppingItems = pendingShopping,
                    pendingRoutines = pendingRoutines,
                    todayRoutinesTotal = pendingRoutines.size + completedToday.size,
                    todayRoutinesDone = completedToday.size,
                    todayByMember = tallyByMember(completedToday, household),
                    currentUserId = currentUserId,
                    isOffline = !isOnline
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

    private fun tallyByMember(completedToday: List<Routine>, household: Household?): List<MemberTally> {
        if (household == null) return emptyList()

        val counts = completedToday
            .filter { it.type == RoutineType.HOUSEHOLD }
            .mapNotNull { it.lastCompletedBy?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()

        if (counts.isEmpty()) return emptyList()

        val names = getMemberProfilesUseCase(household).associate { profile ->
            profile.id to profile.nickname.ifBlank { profile.displayName }
        }

        return counts.entries
            .map { (memberId, count) -> MemberTally(memberId, names[memberId].orEmpty(), count) }
            .sortedByDescending { it.count }
    }

    fun onToggleRoutine(routine: Routine) {
        val state = uiState.value
        val householdId = state.household?.id ?: return
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
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
