package com.monsteraltech.habitly.feature.routines.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.AddRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.CancelReminderUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.DeleteRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ObserveRoutinesUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ReorderRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ScheduleReminderUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ToggleRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.UpdateRoutineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class RoutinesUiState(
    val routines: List<Routine> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String = "",
    val currentHouseholdId: String = "",
    val memberNicknames: Map<String, String> = emptyMap()
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val observeRoutinesUseCase: ObserveRoutinesUseCase,
    private val addRoutineUseCase: AddRoutineUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val deleteRoutineUseCase: DeleteRoutineUseCase,
    private val updateRoutineUseCase: UpdateRoutineUseCase,
    private val reorderRoutineUseCase: ReorderRoutineUseCase,
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
                    val result = getMemberProfilesUseCase(household.members)
                    if (result.isSuccess) {
                        val nicknames = result.getOrDefault(emptyList()).associate { 
                            it.id to it.nickname.ifBlank { it.displayName } 
                        }
                        _uiState.update { it.copy(memberNicknames = nicknames) }
                    }
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
                    _uiState.update { it.copy(routines = routines, isLoading = false) }
                }
        }
    }

    fun onAddRoutine(title: String, description: String, type: RoutineType, frequency: RoutineFrequency, scheduledDays: List<Int>, reminderTime: Int?) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return
        
        viewModelScope.launch {
            addRoutineUseCase(state.currentUserId, state.currentHouseholdId, title, description, type, frequency, scheduledDays, reminderTime)
        }
    }

    fun onToggleRoutine(routine: Routine) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return
        
        val isCompletedNow = isRoutineCompletedToday(routine)
        
        viewModelScope.launch {
            toggleRoutineUseCase(state.currentUserId, state.currentHouseholdId, routine, !isCompletedNow)
        }
    }

    fun onDeleteRoutine(routine: Routine) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return
        
        viewModelScope.launch {
            deleteRoutineUseCase(state.currentUserId, state.currentHouseholdId, routine)
        }
    }

    fun onEditRoutine(routine: Routine, title: String, description: String, frequency: RoutineFrequency, scheduledDays: List<Int>, reminderTime: Int?) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return
        
        viewModelScope.launch {
            updateRoutineUseCase(state.currentUserId, state.currentHouseholdId, routine, title, description, frequency, scheduledDays, reminderTime)
        }
    }

    fun onToggleReminder(routine: Routine) {
        viewModelScope.launch {
            if (routine.reminderTime != null) {
                scheduleReminderUseCase(routine)
            } else {
                cancelReminderUseCase(routine.id)
            }
        }
    }

    fun onReorderRoutine(type: RoutineType, orderedIds: List<String>) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.currentHouseholdId.isBlank()) return
        
        viewModelScope.launch {
            reorderRoutineUseCase(state.currentUserId, state.currentHouseholdId, type, orderedIds)
        }
    }
    
    companion object {
        fun isRoutineCompletedToday(routine: Routine): Boolean {
            val lastCompleted = routine.lastCompletedAt ?: return false
            val today = Calendar.getInstance()
            val completedDay = Calendar.getInstance().apply { timeInMillis = lastCompleted }

            val isSameDay = today.get(Calendar.YEAR) == completedDay.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == completedDay.get(Calendar.DAY_OF_YEAR)

            if (isSameDay) return true

            if (routine.frequency == RoutineFrequency.DAILY) return false

            val todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK)
            if (!routine.isScheduledForDayOfWeek(todayDayOfWeek)) return false

            return false
        }
    }
}
