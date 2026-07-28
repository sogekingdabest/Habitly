package com.monsteraltech.habitly.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.feature.household.domain.usecase.DeleteAccountUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveUserProfileUseCase
import com.monsteraltech.habitly.feature.household.domain.usecase.UpdateNicknameUseCase
import com.monsteraltech.habitly.feature.login.domain.model.ReauthenticationRequiredException
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import com.monsteraltech.habitly.feature.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val observeUserProfileUseCase: ObserveUserProfileUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "unknown_user"

    init {
        _uiState.update { it.copy(email = firebaseAuth.currentUser?.email.orEmpty()) }

        viewModelScope.launch {
            combine(
                settingsRepository.themeMode,
                settingsRepository.language,
                settingsRepository.remindersEnabled
            ) { theme, language, reminders -> Triple(theme, language, reminders) }
                .collect { (theme, language, reminders) ->
                    _uiState.update {
                        it.copy(themeMode = theme, language = language, remindersEnabled = reminders)
                    }
                }
        }

        viewModelScope.launch {
            observeUserProfileUseCase(currentUserId).collectLatest { profile ->
                _uiState.update { it.copy(nickname = profile?.nickname.orEmpty()) }
                // Se guarda aparte del estado de UI: el nickname se duplica dentro del
                // documento de la casa y hace falta saber a cuál escribir.
                activeHouseholdId = profile?.activeHouseholdId.orEmpty()
            }
        }
    }

    private var activeHouseholdId: String = ""

    fun onThemeModeSelected(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    /** Persiste el idioma. La pantalla llama a `recreate()` para que se aplique la locale. */
    fun onLanguageSelected(language: AppLanguage) = settingsRepository.setLanguage(language)

    fun onRemindersToggled(enabled: Boolean) = settingsRepository.setRemindersEnabled(enabled)

    fun onUpdateNickname(newNickname: String) {
        if (currentUserId == "unknown_user" || newNickname.isBlank()) return
        viewModelScope.launch {
            val result = updateNicknameUseCase(currentUserId, activeHouseholdId, newNickname)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun onSignOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }

    fun onDeleteAccount(onComplete: () -> Unit) {
        if (_uiState.value.isDeletingAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, deleteAccountError = null) }
            val result = deleteAccountUseCase()
            if (result.isSuccess) {
                _uiState.update { it.copy(isDeletingAccount = false) }
                onComplete()
            } else {
                val error = result.exceptionOrNull()
                val message = if (error is ReauthenticationRequiredException) {
                    "Por seguridad, vuelve a iniciar sesión y reinténtalo para completar el borrado."
                } else {
                    error?.message ?: "No se pudo borrar la cuenta"
                }
                _uiState.update { it.copy(isDeletingAccount = false, deleteAccountError = message) }
            }
        }
    }

    fun onDeleteAccountErrorShown() {
        _uiState.update { it.copy(deleteAccountError = null) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
}
