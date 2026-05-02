package com.monsteraltech.habitly.feature.login.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.monsteraltech.habitly.feature.login.domain.usecase.LoginWithEmailUseCase
import com.monsteraltech.habitly.feature.login.domain.usecase.LoginWithGoogleUseCase

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginWithEmailUseCase: LoginWithEmailUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.EmailChanged -> {
                _uiState.update { it.copy(email = event.email, errorMessage = null) }
            }
            is LoginEvent.PasswordChanged -> {
                _uiState.update { it.copy(password = event.password, errorMessage = null) }
            }
            is LoginEvent.LoginClicked -> {
                performLogin()
            }
            is LoginEvent.GoogleAuthTokenReceived -> {
                performGoogleLogin(event.idToken)
            }
            is LoginEvent.GoogleLoginError -> {
                _uiState.update { it.copy(isLoading = false, errorMessage = event.error) }
            }
        }
    }

    private fun performLogin() {
        val currentState = _uiState.value
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = loginWithEmailUseCase(currentState.email, currentState.password)
            
            result.onSuccess {
                _uiState.update { state -> 
                    state.copy(isLoading = false, isLoginSuccessful = true) 
                }
            }.onFailure { error ->
                _uiState.update { state -> 
                    state.copy(isLoading = false, errorMessage = error.message ?: "Error desconocido al hacer login") 
                }
            }
        }
    }

    private fun performGoogleLogin(idToken: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = loginWithGoogleUseCase(idToken)

            result.onSuccess {
                _uiState.update { state ->
                    state.copy(isLoading = false, isLoginSuccessful = true)
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(isLoading = false, errorMessage = error.message ?: "Error desconocido en inicio de sesión con Google")
                }
            }
        }
    }
}
