package com.monsteraltech.habitly.feature.routines.presentation.add

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.household.domain.usecase.GetMemberProfilesUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.AddRoutineUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ScheduleReminderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddRoutineUiState(
    val title: String = "",
    val description: String = "",
    val type: RoutineType = RoutineType.PERSONAL,
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val selectedDays: List<Int> = emptyList(),
    val intervalDays: Int = DEFAULT_INTERVAL_DAYS,
    val reminderEnabled: Boolean = false,
    /** Hora del aviso en minutos desde medianoche; solo cuenta con [reminderEnabled]. */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val rotationEnabled: Boolean = false,
    val assignedTo: String? = null,
    val householdMembers: List<String> = emptyList(),
    val memberNicknames: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val success: Boolean = false
) {
    val needsDays: Boolean
        get() = frequency == RoutineFrequency.WEEKLY || frequency == RoutineFrequency.CUSTOM

    /** La rotación solo tiene sentido en una casa con más de un miembro. */
    val canRotate: Boolean
        get() = type == RoutineType.HOUSEHOLD && householdMembers.size > 1

    val canSave: Boolean
        get() = title.isNotBlank() &&
            (frequency != RoutineFrequency.WEEKLY || selectedDays.isNotEmpty())
}

@HiltViewModel
class AddRoutineViewModel @Inject constructor(
    private val addRoutineUseCase: AddRoutineUseCase,
    private val scheduleReminderUseCase: ScheduleReminderUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val observeHouseholdUseCase: ObserveHouseholdUseCase,
    private val getMemberProfilesUseCase: GetMemberProfilesUseCase,
    firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddRoutineUiState(
            type = savedStateHandle.get<String>(TYPE_ARG)
                ?.let { name -> RoutineType.entries.find { it.name == name } }
                ?: RoutineType.PERSONAL
        )
    )
    val uiState: StateFlow<AddRoutineUiState> = _uiState.asStateFlow()

    private val currentUserId: String = firebaseAuth.currentUser?.uid ?: ""
    private var currentHouseholdId: String = ""

    init {
        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId)
                .mapNotNull { it?.activeHouseholdId }
                .distinctUntilChanged()
                .collectLatest { householdId ->
                    currentHouseholdId = householdId
                    observeMembers(householdId)
                }
        }
    }

    private suspend fun observeMembers(householdId: String) {
        observeHouseholdUseCase(householdId).collectLatest { household ->
            if (household == null) return@collectLatest
            val nicknames = getMemberProfilesUseCase(household)
                .filter { it.nickname.isNotBlank() || it.displayName.isNotBlank() }
                .associate { it.id to it.nickname.ifBlank { it.displayName } }
            _uiState.update {
                it.copy(householdMembers = household.members, memberNicknames = nicknames)
            }
        }
    }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onDescriptionChange(description: String) = _uiState.update { it.copy(description = description) }

    fun onTypeChange(type: RoutineType) = _uiState.update {
        if (type == RoutineType.PERSONAL) {
            it.copy(type = type, rotationEnabled = false, assignedTo = null)
        } else {
            it.copy(type = type)
        }
    }

    fun onFrequencyChange(frequency: RoutineFrequency) = _uiState.update {
        val keepsDays = frequency == RoutineFrequency.WEEKLY || frequency == RoutineFrequency.CUSTOM
        it.copy(frequency = frequency, selectedDays = if (keepsDays) it.selectedDays else emptyList())
    }

    fun onDayToggle(day: Int) = _uiState.update {
        it.copy(
            selectedDays = if (it.selectedDays.contains(day)) it.selectedDays - day
            else it.selectedDays + day
        )
    }

    fun onIntervalChange(delta: Int) = _uiState.update {
        it.copy(intervalDays = (it.intervalDays + delta).coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS))
    }

    fun onReminderToggle(enabled: Boolean) = _uiState.update { it.copy(reminderEnabled = enabled) }

    fun onReminderTimeChange(hour: Int, minute: Int) =
        _uiState.update { it.copy(reminderMinutes = hour * 60 + minute) }

    fun onRotationToggle(enabled: Boolean) = _uiState.update {
        it.copy(
            rotationEnabled = enabled,
            // Al activarla hay que empezar por alguien.
            assignedTo = when {
                enabled && it.assignedTo == null -> it.householdMembers.firstOrNull()
                !enabled -> null
                else -> it.assignedTo
            }
        )
    }

    fun onAssignedToChange(memberId: String) = _uiState.update { it.copy(assignedTo = memberId) }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave || state.isSaving) return
        if (currentUserId.isBlank() || currentHouseholdId.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.routines_error_save) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorRes = null) }
            val rotates = state.canRotate && state.rotationEnabled
            addRoutineUseCase(
                userId = currentUserId,
                householdId = currentHouseholdId,
                title = state.title,
                description = state.description,
                type = state.type,
                frequency = state.frequency,
                scheduledDays = state.selectedDays,
                reminderTime = if (state.reminderEnabled) state.reminderMinutes else null,
                intervalDays = if (state.frequency == RoutineFrequency.EVERY_N_DAYS) state.intervalDays else null,
                rotationEnabled = rotates,
                assignedTo = if (rotates) state.assignedTo else null
            )
                .onSuccess { routine ->
                    scheduleReminderUseCase(routine, currentUserId, currentHouseholdId)
                    _uiState.update { it.copy(isSaving = false, success = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, errorRes = R.string.routines_error_save) }
                }
        }
    }

    companion object {
        const val TYPE_ARG = "type"
    }
}

private const val DEFAULT_INTERVAL_DAYS = 3
private const val MIN_INTERVAL_DAYS = 1
private const val MAX_INTERVAL_DAYS = 365

/** Las 09:00, una hora de aviso razonable por defecto. */
private const val DEFAULT_REMINDER_MINUTES = 9 * 60
