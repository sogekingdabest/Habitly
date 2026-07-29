package com.monsteraltech.habitly.feature.routines.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.AdvanceRotationUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.CancelReminderUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.DeleteRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.GetHouseholdBalanceUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.GetRoutineCompletionsUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ObserveRoutinesUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ReorderRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ReturnRotationUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ScheduleReminderUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ToggleRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.UpdateRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** A routine's detail: the completion calendar for the month being viewed. */
data class RoutineDetailState(
    val routine: Routine,
    val month: YearMonth,
    val completedDates: Set<LocalDate> = emptySet(),
    val isLoading: Boolean = true
) {
    /** How many times it was due in the part of the month already elapsed. */
    fun expectedInMonth(today: LocalDate = LocalDate.now()): Int {
        val to = minOf(month.atEndOfMonth(), today)
        return RoutineSchedule.expectedOccurrences(routine, month.atDay(1), to)
    }

    /** Completion for the month, 0 to 1. Null if it was not yet due even once. */
    fun completionRate(today: LocalDate = LocalDate.now()): Float? {
        val expected = expectedInMonth(today)
        if (expected <= 0) return null
        val done = completedDates.count { it.month == month.month && it.year == month.year }
        return (done.toFloat() / expected).coerceIn(0f, 1f)
    }
}

data class RoutinesUiState(
    val routines: List<Routine> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    @StringRes val errorRes: Int? = null,
    val currentUserId: String = "",
    val currentHouseholdId: String = "",
    val memberNicknames: Map<String, String> = emptyMap(),
    /** Household members in the order the rotating-routine turn follows. */
    val householdMembers: List<String> = emptyList(),
    val routineDetail: RoutineDetailState? = null,
    /** Household routines completed per member in the current week. */
    val weeklyBalance: Map<String, Int> = emptyMap()
) {
    /** Total household routines completed this week by everyone. */
    val weeklyBalanceTotal: Int
        get() = weeklyBalance.values.sum()
}

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val observeRoutinesUseCase: ObserveRoutinesUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val deleteRoutineUseCase: DeleteRoutineUseCase,
    private val updateRoutineUseCase: UpdateRoutineUseCase,
    private val reorderRoutineUseCase: ReorderRoutineUseCase,
    private val getRoutineCompletionsUseCase: GetRoutineCompletionsUseCase,
    private val advanceRotationUseCase: AdvanceRotationUseCase,
    private val returnRotationUseCase: ReturnRotationUseCase,
    private val getHouseholdBalanceUseCase: GetHouseholdBalanceUseCase,
    private val scheduleReminderUseCase: ScheduleReminderUseCase,
    private val cancelReminderUseCase: CancelReminderUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutinesUiState())
    val uiState: StateFlow<RoutinesUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private var observeJob: Job? = null
    private var detailJob: Job? = null
    private var balanceJob: Job? = null

    init {
        _uiState.update { it.copy(currentUserId = currentUserId) }
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId).collectLatest { profile ->
                val householdId = profile?.activeHouseholdId ?: ""
                _uiState.update { it.copy(currentHouseholdId = householdId) }
                if (householdId.isNotEmpty()) {
                    startObservingRoutines(currentUserId, householdId)
                    loadMemberNicknames(householdId)
                }
            }
        }
    }

    private fun loadMemberNicknames(householdId: String) {
        viewModelScope.launch {
            observeHouseholdUseCase(householdId).collectLatest { household ->
                if (household != null) {
                    // The names live inside the household document itself: resolved in memory,
                    // with no per-member Firestore read.
                    val nicknames = getMemberProfilesUseCase(household)
                        .filter { it.nickname.isNotBlank() || it.displayName.isNotBlank() }
                        .associate { it.id to it.nickname.ifBlank { it.displayName } }
                    _uiState.update {
                        it.copy(householdMembers = household.members, memberNicknames = nicknames)
                    }
                    loadWeeklyBalance()
                }
            }
        }
    }

    private fun startObservingRoutines(userId: String, householdId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeRoutinesUseCase(userId, householdId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { routines ->
                    _uiState.update { state ->
                        state.copy(
                            routines = routines,
                            isLoading = false,
                            // The open detail sheet stays in sync with the fresh data.
                            routineDetail = state.routineDetail?.let { detail ->
                                routines.find { it.id == detail.routine.id }
                                    ?.let { detail.copy(routine = it) }
                                    ?: detail
                            }
                        )
                    }
                }
        }
    }

    fun onToggleRoutine(routine: Routine) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return

        val isCompletedNow = RoutineSchedule.isCompletedOn(routine, LocalDate.now())
        val willComplete = !isCompletedNow

        viewModelScope.launch {
            toggleRoutineUseCase(state.currentUserId, state.currentHouseholdId, routine, willComplete)
                .onSuccess {
                    if (willComplete) {
                        // Completed: the turn moves to the next member.
                        advanceRotationUseCase(
                            state.currentUserId,
                            state.currentHouseholdId,
                            routine,
                            state.householdMembers
                        )
                    } else {
                        // Undone: the turn goes back to whoever unmarked it, not to the previous
                        // holder; unmarking it means it was not really done.
                        returnRotationUseCase(state.currentUserId, state.currentHouseholdId, routine)
                    }
                    refreshDetailIfShowing(routine.id)
                    loadWeeklyBalance()
                }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.routines_error_update) } }
        }
    }

    /** The current week's balance, Monday to Sunday. */
    private fun loadWeeklyBalance() {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return

        balanceJob?.cancel()
        balanceJob = viewModelScope.launch {
            val today = LocalDate.now()
            val monday = today.with(DayOfWeek.MONDAY)
            val result = getHouseholdBalanceUseCase(
                userId = state.currentUserId,
                householdId = state.currentHouseholdId,
                from = monday,
                to = monday.plusDays(6)
            )
            _uiState.update { it.copy(weeklyBalance = result.getOrDefault(emptyMap())) }
        }
    }

    fun onDeleteRoutine(routine: Routine) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return

        viewModelScope.launch {
            deleteRoutineUseCase(state.currentUserId, state.currentHouseholdId, routine)
                .onSuccess { cancelReminderUseCase(routine.id) }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.routines_error_delete) } }
        }
    }

    fun onEditRoutine(
        routine: Routine,
        title: String,
        description: String,
        frequency: RoutineFrequency,
        scheduledDays: List<Int>,
        reminderTime: Int?,
        intervalDays: Int? = routine.intervalDays,
        pausedUntil: Long? = routine.pausedUntil,
        rotationEnabled: Boolean = routine.rotationEnabled,
        assignedTo: String? = routine.assignedTo
    ) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return

        viewModelScope.launch {
            updateRoutineUseCase(
                state.currentUserId, state.currentHouseholdId, routine, title, description,
                frequency, scheduledDays, reminderTime, intervalDays, pausedUntil,
                rotationEnabled, assignedTo
            )
                .onSuccess {
                    // Reschedules with the new data; if reminderTime is null, the use case cancels
                    // the existing work.
                    scheduleReminderUseCase(
                        routine.copy(
                            title = title.trim(),
                            description = description.trim(),
                            frequency = frequency,
                            scheduledDays = scheduledDays,
                            reminderTime = reminderTime,
                            intervalDays = intervalDays,
                            pausedUntil = pausedUntil,
                            rotationEnabled = rotationEnabled,
                            assignedTo = assignedTo
                        ),
                        state.currentUserId,
                        state.currentHouseholdId
                    )
                }
                .onFailure { _uiState.update { it.copy(errorRes = R.string.routines_error_save) } }
        }
    }

    /** Turns holiday mode on until the given date, or off (null = resume). */
    fun onSetPaused(routine: Routine, pausedUntil: Long?) {
        onEditRoutine(
            routine = routine,
            title = routine.title,
            description = routine.description,
            frequency = routine.frequency,
            scheduledDays = routine.scheduledDays,
            reminderTime = routine.reminderTime,
            intervalDays = routine.intervalDays,
            pausedUntil = pausedUntil
        )
    }

    fun onReorderRoutine(type: RoutineType, orderedIds: List<String>) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return

        viewModelScope.launch {
            reorderRoutineUseCase(state.currentUserId, state.currentHouseholdId, type, orderedIds)
                .onFailure { _uiState.update { it.copy(errorRes = R.string.routines_error_update) } }
        }
    }

    // ---------- Detail sheet with the completion calendar ----------

    fun onOpenRoutineDetail(routine: Routine) {
        _uiState.update {
            it.copy(routineDetail = RoutineDetailState(routine = routine, month = YearMonth.now()))
        }
        loadCompletions()
    }

    fun onCloseRoutineDetail() {
        detailJob?.cancel()
        _uiState.update { it.copy(routineDetail = null) }
    }

    fun onDetailMonthShift(months: Long) {
        val detail = _uiState.value.routineDetail ?: return
        val target = detail.month.plusMonths(months)
        // Do not let it move past the current month: there is nothing to show.
        if (target.isAfter(YearMonth.now())) return

        _uiState.update {
            it.copy(routineDetail = detail.copy(month = target, isLoading = true))
        }
        loadCompletions()
    }

    private fun refreshDetailIfShowing(routineId: String) {
        if (_uiState.value.routineDetail?.routine?.id == routineId) loadCompletions()
    }

    private fun loadCompletions() {
        val state = _uiState.value
        val detail = state.routineDetail ?: return
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val result = getRoutineCompletionsUseCase(
                userId = state.currentUserId,
                householdId = state.currentHouseholdId,
                routine = detail.routine,
                from = detail.month.atDay(1),
                to = detail.month.atEndOfMonth()
            )
            _uiState.update { current ->
                val open = current.routineDetail ?: return@update current
                // The month or routine may have changed while it was loading.
                if (open.month != detail.month || open.routine.id != detail.routine.id) return@update current
                current.copy(
                    routineDetail = open.copy(
                        completedDates = result.getOrDefault(emptyList()).toSet(),
                        isLoading = false
                    )
                )
            }
            if (result.isFailure) {
                _uiState.update { it.copy(errorRes = R.string.routines_error_update) }
            }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorRes = null, error = null) }
    }
}
