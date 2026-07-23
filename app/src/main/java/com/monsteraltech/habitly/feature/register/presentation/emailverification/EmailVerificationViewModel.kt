package com.monsteraltech.habitly.feature.register.presentation.emailverification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import com.monsteraltech.habitly.feature.register.domain.usecase.CheckEmailVerificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmailVerificationViewModel @Inject constructor(
    private val checkEmailVerificationUseCase: CheckEmailVerificationUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EmailVerificationEffect>(Channel.BUFFERED)
    val effects: Flow<EmailVerificationEffect> = _effects.receiveAsFlow()

    private var pollingJob: Job? = null
    private var cooldownJob: Job? = null

    init {
        val userEmail = authRepository.getCurrentUser()?.email ?: ""
        _uiState.update { it.copy(userEmail = userEmail) }
        startPolling()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private fun startPolling() {
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(3000) // Poll every 3 seconds
                val result = checkEmailVerificationUseCase()
                result.onSuccess { isVerified ->
                    if (isVerified) {
                        _uiState.update { it.copy(isVerified = true) }
                        stopPolling()
                        _effects.send(EmailVerificationEffect.NavigateToHome)
                    }
                }.onFailure { error ->
                    val typedError = error as? RegisterError ?: RegisterError.Unknown(error)
                    _uiState.update { it.copy(error = typedError) }
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun onIntent(intent: EmailVerificationIntent) {
        when (intent) {
            is EmailVerificationIntent.CheckVerificationClicked -> {
                _uiState.update { it.copy(isCheckingVerification = true, error = null) }
                viewModelScope.launch {
                    val result = checkEmailVerificationUseCase()
                    result.onSuccess { isVerified ->
                        _uiState.update { it.copy(isCheckingVerification = false) }
                        if (isVerified) {
                            stopPolling()
                            _uiState.update { it.copy(isVerified = true) }
                            _effects.send(EmailVerificationEffect.NavigateToHome)
                        } else {
                            _effects.send(EmailVerificationEffect.ShowSnackbar("Aún no verificado. Revisa tu bandeja de entrada."))
                        }
                    }.onFailure { error ->
                        val typedError = error as? RegisterError ?: RegisterError.Unknown(error)
                         _uiState.update { 
                             it.copy(
                                 isCheckingVerification = false,
                                 error = typedError
                             )
                         }
                    }
                }
            }
            is EmailVerificationIntent.ResendEmailClicked -> {
                if (_uiState.value.resendCooldownSeconds == 0) {
                   _uiState.update { it.copy(isResendingEmail = true) }
                   viewModelScope.launch {
                       val result = authRepository.resendVerificationEmail()
                       _uiState.update { it.copy(isResendingEmail = false) }
                       result.onSuccess {
                           _effects.send(EmailVerificationEffect.ShowSnackbar("Correo de verificación reenviado"))
                           startCooldown()
                       }.onFailure {
                           // Sin cooldown en el fallo: que el usuario pueda reintentar de inmediato.
                           _effects.send(EmailVerificationEffect.ShowSnackbar("No se pudo reenviar el correo"))
                       }
                   }
                }
            }
            is EmailVerificationIntent.CancelClicked -> {
                stopPolling()
                viewModelScope.launch {
                    authRepository.signOut()
                    _effects.send(EmailVerificationEffect.NavigateToRegister)
                }
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        _uiState.update { it.copy(resendCooldownSeconds = 60) }
        cooldownJob = viewModelScope.launch {
            while (_uiState.value.resendCooldownSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(resendCooldownSeconds = it.resendCooldownSeconds - 1) }
            }
        }
    }
}
