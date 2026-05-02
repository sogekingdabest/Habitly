package com.monsteraltech.habitly.feature.register.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import com.monsteraltech.habitly.feature.register.domain.usecase.RegisterWithEmailUseCase
import com.monsteraltech.habitly.feature.register.domain.usecase.SignInWithGoogleUseCase
import com.monsteraltech.habitly.feature.register.domain.usecase.ValidateRegisterInputUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerWithEmailUseCase: RegisterWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val validateRegisterInputUseCase: ValidateRegisterInputUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _effects = Channel<RegisterEffect>(Channel.BUFFERED)
    val effects: Flow<RegisterEffect> = _effects.receiveAsFlow()

    init {
        checkActiveSession()
    }

    private fun checkActiveSession() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            viewModelScope.launch {
                _effects.send(RegisterEffect.NavigateToHome)
            }
        }
    }

    fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.DisplayNameChanged -> {
                _uiState.update { 
                    it.copy(
                        displayName = intent.value,
                        displayNameError = null
                    ).recalculateButtonEnabled()
                }
            }
            is RegisterIntent.EmailChanged -> {
                _uiState.update { 
                    it.copy(
                        email = intent.value,
                        emailError = null
                    ).recalculateButtonEnabled()
                }
            }
            is RegisterIntent.PasswordChanged -> {
                _uiState.update { 
                    it.copy(
                        password = intent.value,
                        passwordError = null
                    ).recalculateButtonEnabled()
                }
            }
            is RegisterIntent.ConfirmPasswordChanged -> {
                _uiState.update { 
                    it.copy(
                        confirmPassword = intent.value,
                        confirmPasswordError = null
                    ).recalculateButtonEnabled()
                }
            }
            is RegisterIntent.TogglePasswordVisibility -> {
                _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            is RegisterIntent.ToggleConfirmPasswordVisibility -> {
                _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
            }
            is RegisterIntent.RegisterWithEmailClicked -> registerWithEmail()
            is RegisterIntent.SignInWithGoogleClicked -> {
                _uiState.update { it.copy(isGoogleSignInLoading = true, isRegisterButtonEnabled = false) }
                viewModelScope.launch {
                    _effects.send(RegisterEffect.LaunchGoogleSignIn)
                }
            }
            is RegisterIntent.GoogleIdTokenReceived -> handleGoogleIdToken(intent.idToken)
            is RegisterIntent.GoogleSignInCancelled -> {
                _uiState.update { 
                    it.copy(
                        isGoogleSignInLoading = false,
                        globalError = RegisterError.GoogleSignInCancelled
                    ).recalculateButtonEnabled()
                }
            }
            is RegisterIntent.GoogleSignInFailed -> {
                _uiState.update { 
                    it.copy(
                        isGoogleSignInLoading = false,
                        globalError = RegisterError.GoogleSignInFailed
                    ).recalculateButtonEnabled()
                }
            }
            is RegisterIntent.NavigateToLoginClicked -> {
                viewModelScope.launch {
                    _effects.send(RegisterEffect.NavigateToLogin)
                }
            }
            is RegisterIntent.ErrorDismissed -> {
                _uiState.update { it.copy(globalError = null) }
            }
        }
    }

    private fun registerWithEmail() {
        val state = _uiState.value
        val credentials = RegisterCredentials(
            email = state.email,
            password = state.password,
            confirmPassword = state.confirmPassword,
            displayName = state.displayName
        )

        // Validacion
        val errors = validateRegisterInputUseCase(credentials)
        if (errors.isNotEmpty()) {
            _uiState.update { it.applyErrors(errors) }
            return
        }

        _uiState.update { it.copy(isLoading = true, isRegisterButtonEnabled = false, globalError = null) }

        viewModelScope.launch {
            val result = registerWithEmailUseCase(credentials)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false).recalculateButtonEnabled() }
                _effects.send(RegisterEffect.NavigateToEmailVerification)
            }.onFailure { error ->
                val typedError = error as? RegisterError ?: RegisterError.Unknown(error)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        globalError = typedError
                    ).recalculateButtonEnabled()
                }
            }
        }
    }

    private fun handleGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            val result = signInWithGoogleUseCase(idToken)
            result.onSuccess {
                _uiState.update { it.copy(isGoogleSignInLoading = false).recalculateButtonEnabled() }
                _effects.send(RegisterEffect.NavigateToHome)
            }.onFailure { error ->
                val typedError = error as? RegisterError ?: RegisterError.Unknown(error)
                _uiState.update { 
                    it.copy(
                        isGoogleSignInLoading = false,
                        globalError = typedError
                    ).recalculateButtonEnabled()
                }
            }
        }
    }

    private fun RegisterUiState.recalculateButtonEnabled(): RegisterUiState {
        val enabled = displayName.isNotBlank() && 
                      email.isNotBlank() && 
                      password.isNotBlank() && 
                      confirmPassword.isNotBlank() &&
                      !isLoading && 
                      !isGoogleSignInLoading
        return this.copy(isRegisterButtonEnabled = enabled)
    }

    private fun RegisterUiState.applyErrors(errors: List<RegisterError>): RegisterUiState {
        var newState = this
        errors.forEach { error ->
            when (error) {
                is RegisterError.DisplayNameBlank, is RegisterError.DisplayNameTooShort -> 
                    newState = newState.copy(displayNameError = error)
                is RegisterError.EmailBlank, is RegisterError.EmailInvalidFormat -> 
                    newState = newState.copy(emailError = error)
                is RegisterError.PasswordTooShort, is RegisterError.PasswordNoUppercase, is RegisterError.PasswordNoDigit -> 
                    newState = newState.copy(passwordError = error)
                is RegisterError.PasswordsDoNotMatch -> 
                    newState = newState.copy(confirmPasswordError = error)
                else -> {}
            }
        }
        return newState
    }
}
