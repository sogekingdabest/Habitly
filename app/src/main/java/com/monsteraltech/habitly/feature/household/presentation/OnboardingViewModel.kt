package com.monsteraltech.habitly.feature.household.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.usecase.CreateHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.JoinHouseholdUseCase
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.AddRoutineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Onboarding steps: the household details, then the routines to start with. */
enum class OnboardingStep { FORM, TEMPLATES }

/**
 * A routine ticked in the templates step, with the title **already resolved** by the screen — the
 * ViewModel does not touch resources, which is how the Settings language is respected.
 */
data class NewHouseholdRoutine(
    val title: String,
    val frequency: RoutineFrequency,
    val scheduledDays: List<Int> = emptyList(),
    val intervalDays: Int? = null
)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.FORM,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createHouseholdUseCase: CreateHouseholdUseCase,
    private val joinHouseholdUseCase: JoinHouseholdUseCase,
    private val addRoutineUseCase: AddRoutineUseCase,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val userId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private val displayName: String
        get() = firebaseAuth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: firebaseAuth.currentUser?.email?.substringBefore("@")
            ?: "Usuario"

    // On success the profile gains an activeHouseholdId and MainViewModel switches to the main
    // screen by itself; only the error path is handled here.

    /**
     * From the form to the templates step. **The household is not created yet**: the moment it
     * exists the profile points at it and `MainViewModel` changes screen, so a step placed after
     * creation would never be seen.
     */
    fun onContinueToTemplates() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(step = OnboardingStep.TEMPLATES, error = null) }
    }

    fun onBackToForm() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(step = OnboardingStep.FORM, error = null) }
    }

    /**
     * Creates the household and, inside it, the ticked routines. An empty [routines] is exactly
     * the "I'd rather start from scratch" path.
     *
     * The routines are created **before** returning, in the same call. They are household
     * routines, born without reminders — scheduling eight routines unasked would be intrusive —
     * and without rotation, editable like any other.
     */
    fun onCreateHousehold(name: String, routines: List<NewHouseholdRoutine> = emptyList()) {
        if (_uiState.value.isSubmitting || userId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            createHouseholdUseCase(userId, displayName, name).fold(
                onSuccess = { householdId ->
                    addTemplateRoutines(householdId, routines)
                    _uiState.update { it.copy(isSubmitting = false) }
                },
                onFailure = { error ->
                    // Back to the form: the household name is what needs fixing.
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            step = OnboardingStep.FORM,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    /**
     * Creates the ticked templates. A failure here **does not become a screen error**: the
     * household already exists and the app is about to switch to the main screen; trapping the
     * user with a red error would be worse than routines they can recreate by hand.
     */
    private suspend fun addTemplateRoutines(
        householdId: String,
        routines: List<NewHouseholdRoutine>
    ) {
        for (routine in routines) {
            addRoutineUseCase(
                userId = userId,
                householdId = householdId,
                title = routine.title,
                description = "",
                type = RoutineType.HOUSEHOLD,
                frequency = routine.frequency,
                scheduledDays = routine.scheduledDays,
                reminderTime = null,
                intervalDays = routine.intervalDays
            )
        }
    }

    fun onJoinHousehold(inviteCode: String) {
        if (_uiState.value.isSubmitting || userId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = joinHouseholdUseCase(userId, inviteCode)
            _uiState.update {
                it.copy(isSubmitting = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    /** Signs out of Firebase (local data untouched) and signals navigation back to login. */
    fun onSignOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
}
