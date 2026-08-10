package com.monsteraltech.habitly.feature.register.presentation.register

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import com.monsteraltech.habitly.navigation.AuthRoute
import com.monsteraltech.habitly.feature.login.presentation.findActivity
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyTextButton
import com.monsteraltech.habitly.ui.components.HabitlyTextField
import com.monsteraltech.habitly.ui.components.IconHalo
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.theme.habitly
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
    val webClientId = stringResource(R.string.default_web_client_id)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(webClientId) {
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
                        } catch (e: NoCredentialException) {
                            Log.i("RegisterScreen", "No Google credentials are available", e)
                            viewModel.onIntent(RegisterIntent.GoogleSignInFailed)
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

    HabitlyBackground(arrangement = MeshArrangement.Auth) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .padding(horizontal = 26.dp, vertical = 12.dp)
            ) {
                // --- Scrollable top part ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    IconHalo(size = 74.dp, cornerRadius = 24.dp) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAddAlt1,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(16.dp).fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.register_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    HabitlyTextField(
                        value = state.displayName,
                        onValueChange = { viewModel.onIntent(RegisterIntent.DisplayNameChanged(it)) },
                        label = stringResource(R.string.register_name),
                        isError = state.displayNameError != null,
                        supportingText = {
                            if (state.displayNameError != null) {
                                Text(
                                    text = getErrorMessage(state.displayNameError!!),
                                    modifier = Modifier.testTag("register_display_name_error")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("register_display_name_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    HabitlyTextField(
                        value = state.email,
                        onValueChange = { viewModel.onIntent(RegisterIntent.EmailChanged(it)) },
                        label = stringResource(R.string.common_email),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        isError = state.emailError != null,
                        supportingText = {
                            if (state.emailError != null) {
                                Text(
                                    text = getErrorMessage(state.emailError!!),
                                    modifier = Modifier.testTag("register_email_error")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("register_email_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    HabitlyTextField(
                        value = state.password,
                        onValueChange = { viewModel.onIntent(RegisterIntent.PasswordChanged(it)) },
                        label = stringResource(R.string.common_password),
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
                                Icon(
                                    imageVector = image,
                                    contentDescription = stringResource(
                                        if (state.isPasswordVisible) R.string.register_hide_password else R.string.register_show_password
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("register_password_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    HabitlyTextField(
                        value = state.confirmPassword,
                        onValueChange = { viewModel.onIntent(RegisterIntent.ConfirmPasswordChanged(it)) },
                        label = stringResource(R.string.register_confirm_password),
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
                                Icon(
                                    imageVector = image,
                                    contentDescription = stringResource(
                                        if (state.isConfirmPasswordVisible) R.string.register_hide_password else R.string.register_show_password
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("register_confirm_password_field")
                    )

                    if (state.globalError != null) {
                        Text(
                            text = getErrorMessage(state.globalError!!),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp).testTag("register_global_error_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // --- Fixed bottom part ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HabitlyPrimaryButton(
                        text = stringResource(R.string.register_submit),
                        onClick = { viewModel.onIntent(RegisterIntent.RegisterWithEmailClicked) },
                        enabled = state.isRegisterButtonEnabled,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("register_submit_button")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.habitly.border)
                        Text(
                            text = " ${stringResource(R.string.common_or)} ",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.habitly.navIdle
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.habitly.border)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = { viewModel.onIntent(RegisterIntent.SignInWithGoogleClicked) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("register_google_button"),
                        enabled = !state.isGoogleSignInLoading && !state.isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.habitly.card,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.habitly.border)
                    ) {
                        if (state.isGoogleSignInLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).testTag("register_google_loading_indicator"),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.register_with_google),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.register_have_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.habitly.textSecondary
                        )
                        HabitlyTextButton(
                            text = stringResource(R.string.register_login_link),
                            onClick = { viewModel.onIntent(RegisterIntent.NavigateToLoginClicked) },
                            modifier = Modifier.testTag("register_navigate_to_login_link")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getErrorMessage(error: RegisterError): String {
    return when (error) {
        is RegisterError.DisplayNameBlank -> stringResource(R.string.register_error_name_blank)
        is RegisterError.DisplayNameTooShort -> stringResource(R.string.register_error_name_short)
        is RegisterError.EmailBlank -> stringResource(R.string.register_error_email_blank)
        is RegisterError.EmailInvalidFormat -> stringResource(R.string.register_error_email_invalid)
        is RegisterError.PasswordTooShort -> stringResource(R.string.register_error_password_short)
        is RegisterError.PasswordNoUppercase -> stringResource(R.string.register_error_password_uppercase)
        is RegisterError.PasswordNoDigit -> stringResource(R.string.register_error_password_digit)
        is RegisterError.PasswordsDoNotMatch -> stringResource(R.string.register_error_passwords_mismatch)
        is RegisterError.EmailAlreadyInUse -> stringResource(R.string.register_error_email_in_use)
        is RegisterError.NetworkError -> stringResource(R.string.register_error_network)
        is RegisterError.GoogleSignInCancelled -> stringResource(R.string.register_error_google_cancelled)
        is RegisterError.GoogleSignInFailed -> stringResource(R.string.register_error_google_failed)
        else -> stringResource(R.string.register_error_unknown)
    }
}
