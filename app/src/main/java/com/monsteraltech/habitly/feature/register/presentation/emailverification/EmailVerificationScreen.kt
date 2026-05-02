package com.monsteraltech.habitly.feature.register.presentation.emailverification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import com.monsteraltech.habitly.navigation.AuthRoute
import kotlinx.coroutines.launch

@Composable
fun EmailVerificationScreen(
    viewModel: EmailVerificationViewModel = hiltViewModel(),
    navController: NavController,
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EmailVerificationEffect.NavigateToHome -> {
                    onNavigateToHome()
                }
                is EmailVerificationEffect.NavigateToRegister -> {
                    navController.popBackStack(AuthRoute.Register.route, inclusive = false)
                }
                is EmailVerificationEffect.ShowSnackbar -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
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
                Spacer(modifier = Modifier.height(32.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MarkEmailRead,
                        contentDescription = "Email Enviado",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Verifica tu Email",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Hemos enviado un correo a:",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = state.userEmail,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag("email_verification_email_text")
                )

                Text(
                    text = "Haz clic en el enlace del correo para activar tu cuenta. Estamos esperando tu confirmación...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
                )

                if (state.error != null) {
                    Text(
                        text = "Ocurrió un error: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (!state.isCheckingVerification && !state.isVerified && state.resendCooldownSeconds == 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp).testTag("email_verification_loading"),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Esperando...", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            // --- Parte Inferior Fija ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { viewModel.onIntent(EmailVerificationIntent.CheckVerificationClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("email_verification_check_button"),
                    enabled = !state.isCheckingVerification && !state.isVerified,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isCheckingVerification) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Ya verifiqué mi email", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { viewModel.onIntent(EmailVerificationIntent.ResendEmailClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("email_verification_resend_button"),
                    enabled = state.resendCooldownSeconds == 0 && !state.isResendingEmail,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isResendingEmail) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (state.resendCooldownSeconds > 0) {
                        Text(
                            text = "Reenviar en ${state.resendCooldownSeconds}s",
                            modifier = Modifier.testTag("email_verification_resend_cooldown")
                        )
                    } else {
                        Text("Reenviar email")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = { viewModel.onIntent(EmailVerificationIntent.CancelClicked) },
                    modifier = Modifier.testTag("email_verification_cancel_button")
                ) {
                    Text("Cancelar y volver", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
