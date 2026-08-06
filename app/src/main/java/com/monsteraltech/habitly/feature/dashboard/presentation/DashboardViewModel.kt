package com.monsteraltech.habitly.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.dashboard.data.ConnectivityObserver
import com.monsteraltech.habitly.feature.household.domain.model.Household
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.notes.domain.usecase.ObserveNotesUseCase
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
 * How many household routines a member has done today. [name] arrives empty when their profile is
 * not yet in `Household.memberProfiles` — a household older than the field, or a member who has
 * not opened the app yet — and the UI falls back to placeholder text.
 */
data class MemberTally(val memberId: String, val name: String, val count: Int)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val household: Household? = null,
    val pendingShoppingItems: List<ShoppingItem> = emptyList(),
    val pendingRoutines: List<Routine> = emptyList(),
    /** Routines that count for today: the ones due plus the ones already done. */
    val todayRoutinesTotal: Int = 0,
    val todayRoutinesDone: Int = 0,
    /** Today's split between members, highest first. */
    val todayByMember: List<MemberTally> = emptyList(),
    /** Current uid, used to mark household routines assigned to this user with "your turn". */
    val currentUserId: String = "",
    /** Notes, personal and household together, for the summary card. */
    val notesCount: Int = 0,
    val latestNoteHeading: String = "",
    /** Offline: anything ticked stays on the phone and uploads when the connection returns. */
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
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    // Last routine completed from the dashboard, to offer "Undo" in a snackbar.
    private val _recentlyCompleted = MutableStateFlow<Routine?>(null)
    val recentlyCompleted: StateFlow<Routine?> = _recentlyCompleted.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = observeUserProfileUseCase(currentUserId)
        .mapNotNull { it?.activeHouseholdId }
        .flatMapLatest { householdId ->
            if (householdId.isBlank()) return@flatMapLatest flowOf(DashboardUiState(isLoading = false))

            val householdFlow = observeHouseholdUseCase(householdId)
            val shoppingFlow = observeShoppingListUseCase(householdId)
            val routinesFlow = observeRoutinesUseCase(currentUserId, householdId)
            // Already merges personal and household notes, which keeps this combine within the
            // typed overloads instead of needing the vararg form.
            val notesFlow = observeNotesUseCase(currentUserId, householdId)

            combine(
                householdFlow,
                shoppingFlow,
                routinesFlow,
                notesFlow,
                connectivityObserver.isOnline
            ) { household, shoppingList, routines, notes, isOnline ->
                val today = LocalDate.now()
                val pendingShopping = shoppingList.filter { !it.isChecked }

                // The whole day's split comes from RoutineSchedule, the same source the routines
                // tab and the widget use. Duplicating the filter here would make the numbers
                // diverge the moment a rule changed.
                val pendingRoutines = routines.filter { RoutineSchedule.isPendingOn(it, today) }
                val completedToday = routines.filter { RoutineSchedule.isCompletedOn(it, today) }

                DashboardUiState(
                    isLoading = false,
                    household = household,
                    pendingShoppingItems = pendingShopping,
                    pendingRoutines = pendingRoutines,
                    // Interval routines stop being "due" as soon as they are done, so the total is
                    // built by adding pending and done rather than counting the scheduled ones.
                    todayRoutinesTotal = pendingRoutines.size + completedToday.size,
                    todayRoutinesDone = completedToday.size,
                    todayByMember = tallyByMember(completedToday, household),
                    currentUserId = currentUserId,
                    notesCount = notes.size,
                    latestNoteHeading = notes.firstOrNull()?.heading.orEmpty(),
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

    /**
     * Who did what today, counting **household** routines only: other members' personal routines
     * are never even read, so including them would produce a scoreboard with a single player.
     *
     * Names come from `Household.memberProfiles`, already present in the loaded document — no
     * extra Firestore read.
     */
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
            // The dashboard only lists pending routines, so this is always a completion.
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
