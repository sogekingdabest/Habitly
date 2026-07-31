package com.monsteraltech.habitly.feature.login.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.feature.login.domain.usecase.SendPasswordResetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * @param emailInvalid true when the failure is an email-format one (a different message from the
 *   network one).
 */
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isSent: Boolean = false,
    val error: Boolean = false,
    val emailInvalid: Boolean = false
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sendPasswordResetUseCase: SendPasswordResetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = false, emailInvalid = false) }
    }

    fun onSendClick() {
        val current = _uiState.value
        if (current.isLoading || current.isSent) return
        _uiState.update { it.copy(isLoading = true, error = false, emailInvalid = false) }
        viewModelScope.launch {
            val result = sendPasswordResetUseCase(current.email)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(isLoading = false, isSent = true)
                } else {
                    val invalid = result.exceptionOrNull() is IllegalArgumentException
                    state.copy(isLoading = false, error = !invalid, emailInvalid = invalid)
                }
            }
        }
    }
}
