package com.monsteraltech.habitly.feature.login.presentation

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.login.domain.usecase.LoginWithEmailUseCase
import com.monsteraltech.habitly.feature.login.domain.usecase.LoginWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun performGoogleSignIn(activity: Activity) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activity)
                val webClientId = activity.getString(R.string.default_web_client_id)
                
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.joinToString("") { "%02x".format(it) }
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setNonce(hashedNonce)
                    .setAutoSelectEnabled(false)
                    .build()
                    
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                    
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    onEvent(LoginEvent.GoogleAuthTokenReceived(googleIdTokenCredential.idToken))
                } else {
                    onEvent(LoginEvent.GoogleLoginError("Credencial no válida"))
                }
            } catch (e: GetCredentialCancellationException) {
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: NoCredentialException) {
                onEvent(LoginEvent.GoogleLoginError("No hay ninguna cuenta de Google disponible"))
            } catch (e: GetCredentialException) {
                onEvent(LoginEvent.GoogleLoginError("GetCredentialException: ${e.message}"))
            } catch(e: GoogleIdTokenParsingException) {
                onEvent(LoginEvent.GoogleLoginError("ParsingException: ${e.message}"))
            } catch (e: Exception) {
                onEvent(LoginEvent.GoogleLoginError("Exception [${e.javaClass.simpleName}]: ${e.message}"))
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
