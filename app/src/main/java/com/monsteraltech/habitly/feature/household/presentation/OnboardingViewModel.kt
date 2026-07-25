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

/** Pasos del onboarding: los datos de la casa y, después, las rutinas con las que arrancar. */
enum class OnboardingStep { FORM, TEMPLATES }

/**
 * Rutina marcada en el paso de plantillas, con el título **ya traducido** por la pantalla
 * (el ViewModel no resuelve recursos, así se respeta el idioma de Ajustes).
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

    // Al tener éxito, el perfil pasa a tener activeHouseholdId y el MainViewModel
    // conmuta automáticamente a la pantalla principal; aquí solo gestionamos error.

    /**
     * Del formulario al paso de plantillas. **La casa todavía no se crea**: en cuanto existe, el
     * perfil apunta a ella y `MainViewModel` cambia de pantalla, así que un paso posterior a la
     * creación no llegaría a verse.
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
     * Crea la casa y, dentro de ella, las rutinas marcadas. Con [routines] vacío es exactamente
     * el "prefiero empezar de cero".
     *
     * Las rutinas se crean **antes** de devolver el control, en la misma llamada: son de casa y
     * nacen sin recordatorio (ponerle hora a ocho rutinas sin preguntar sería invasivo) y sin
     * rotación, editables como cualquier otra.
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
                    // Se vuelve al formulario: el nombre de la casa es lo que hay que corregir.
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
     * Alta de las plantillas marcadas. Un fallo aquí **no se convierte en error de pantalla**: la
     * casa ya existe y la app va a conmutar a la principal; dejar al usuario dentro con un error
     * rojo sería peor que unas rutinas que puede volver a crear a mano.
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

    /** Cierra la sesión de Firebase (sin borrar datos locales) y avisa para navegar a login. */
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
