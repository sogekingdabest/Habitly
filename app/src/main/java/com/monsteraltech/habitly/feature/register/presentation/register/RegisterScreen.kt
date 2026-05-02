package com.monsteraltech.habitly.feature.register.presentation.register

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import com.monsteraltech.habitly.navigation.AuthRoute
import com.monsteraltech.habitly.feature.login.presentation.findActivity
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = hiltViewModel(),
    navController: NavController,
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegisterEffect.NavigateToEmailVerification -> {
                    navController.navigate(AuthRoute.EmailVerification.route)
                }
                is RegisterEffect.NavigateToHome -> {
                    onNavigateToHome()
                }
                is RegisterEffect.NavigateToLogin -> {
                    navController.popBackStack()
                }
                is RegisterEffect.LaunchGoogleSignIn -> {
                    coroutineScope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val webClientId = context.getString(R.string.default_web_client_id)
                            
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

                            val activityContext = context.findActivity()
                            val result = credentialManager.getCredential(
                                request = request,
                                context = activityContext
                            )
                            val credential = result.credential
                            if (credential is GoogleIdTokenCredential) {
                                viewModel.onIntent(RegisterIntent.GoogleIdTokenReceived(credential.idToken))
                            } else {
                                viewModel.onIntent(RegisterIntent.GoogleSignInFailed)
                            }
                        } catch (e: GetCredentialCancellationException) {
                            viewModel.onIntent(RegisterIntent.GoogleSignInCancelled)
                        } catch (e: Exception) {
                            Log.e("RegisterScreen", "Google Sign In Failed", e)
                            viewModel.onIntent(RegisterIntent.GoogleSignInFailed)
                        }
                    }
                }
                is RegisterEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(24.dp)
        ) {
            // --- Parte Superior Desplazable ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAddAlt1,
                        contentDescription = "Empezar en Habitly",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Crear Cuenta",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { viewModel.onIntent(RegisterIntent.DisplayNameChanged(it)) },
                    label = { Text("Nombre") },
                    isError = state.displayNameError != null,
                    supportingText = {
                        if (state.displayNameError != null) {
                            Text(
                                text = getErrorMessage(state.displayNameError!!),
                                modifier = Modifier.testTag("register_display_name_error")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("register_display_name_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.onIntent(RegisterIntent.EmailChanged(it)) },
                    label = { Text("Email") },
                    isError = state.emailError != null,
                    supportingText = {
                        if (state.emailError != null) {
                            Text(
                                text = getErrorMessage(state.emailError!!),
                                modifier = Modifier.testTag("register_email_error")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("register_email_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.onIntent(RegisterIntent.PasswordChanged(it)) },
                    label = { Text("Contraseña") },
                    isError = state.passwordError != null,
                    supportingText = {
                        if (state.passwordError != null) {
                            Text(
                                text = getErrorMessage(state.passwordError!!),
                                modifier = Modifier.testTag("register_password_error")
                            )
                        }
                    },
                    visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (state.isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { viewModel.onIntent(RegisterIntent.TogglePasswordVisibility) }) {
                            Icon(imageVector = image, contentDescription = "Ver contraseña")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("register_password_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = { viewModel.onIntent(RegisterIntent.ConfirmPasswordChanged(it)) },
                    label = { Text("Confirmar contraseña") },
                    isError = state.confirmPasswordError != null,
                    supportingText = {
                        if (state.confirmPasswordError != null) {
                            Text(
                                text = getErrorMessage(state.confirmPasswordError!!),
                                modifier = Modifier.testTag("register_confirm_password_error")
                            )
                        }
                    },
                    visualTransformation = if (state.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (state.isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { viewModel.onIntent(RegisterIntent.ToggleConfirmPasswordVisibility) }) {
                            Icon(imageVector = image, contentDescription = "Ver contraseña")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("register_confirm_password_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (state.globalError != null) {
                    Text(
                        text = getErrorMessage(state.globalError!!),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp).testTag("register_global_error_text")
                    )
                }
                
                // Espaciado extra para asegurar que el contenido se pueda mover debajo de los botones
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Parte Inferior Fija ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { viewModel.onIntent(RegisterIntent.RegisterWithEmailClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("register_submit_button"),
                    enabled = state.isRegisterButtonEnabled,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).testTag("register_loading_indicator"),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Unirse a Habitly", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = " o ",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { viewModel.onIntent(RegisterIntent.SignInWithGoogleClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("register_google_button"),
                    enabled = !state.isGoogleSignInLoading && !state.isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isGoogleSignInLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).testTag("register_google_loading_indicator"),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Registrarse con Google", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("¿Ya tienes cuenta?")
                    TextButton(
                        onClick = { viewModel.onIntent(RegisterIntent.NavigateToLoginClicked) },
                        modifier = Modifier.testTag("register_navigate_to_login_link")
                    ) {
                        Text("Inicia sesión", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun getErrorMessage(error: RegisterError): String {
    return when (error) {
        is RegisterError.DisplayNameBlank -> "El nombre no puede estar vacío"
        is RegisterError.DisplayNameTooShort -> "El nombre debe tener al menos 2 caracteres"
        is RegisterError.EmailBlank -> "El email no puede estar vacío"
        is RegisterError.EmailInvalidFormat -> "Formato de email inválido"
        is RegisterError.PasswordTooShort -> "La contraseña debe tener al menos 8 caracteres"
        is RegisterError.PasswordNoUppercase -> "Incluye al menos una letra mayúscula"
        is RegisterError.PasswordNoDigit -> "Incluye al menos un número"
        is RegisterError.PasswordsDoNotMatch -> "Las contraseñas no coinciden"
        is RegisterError.EmailAlreadyInUse -> "Este email ya está registrado"
        is RegisterError.NetworkError -> "Error de red. Revisa tu conexión"
        is RegisterError.GoogleSignInCancelled -> "Inicio de sesión con Google cancelado"
        is RegisterError.GoogleSignInFailed -> "Fallo al iniciar sesión con Google"
        else -> "Ocurrió un error inesperado"
    }
}
