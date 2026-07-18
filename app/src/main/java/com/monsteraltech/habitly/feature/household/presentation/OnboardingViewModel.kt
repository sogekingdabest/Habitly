package com.monsteraltech.habitly.feature.household.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.usecase.CreateHouseholdUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.JoinHouseholdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createHouseholdUseCase: CreateHouseholdUseCase,
    private val joinHouseholdUseCase: JoinHouseholdUseCase,
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

    fun onCreateHousehold(name: String) {
        if (_uiState.value.isSubmitting || userId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = createHouseholdUseCase(userId, displayName, name)
            _uiState.update {
                it.copy(isSubmitting = false, error = result.exceptionOrNull()?.message)
            }
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

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
}
