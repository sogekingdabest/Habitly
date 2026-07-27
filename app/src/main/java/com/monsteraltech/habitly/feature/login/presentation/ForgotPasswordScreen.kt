package com.monsteraltech.habitly.feature.login.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyTextButton
import com.monsteraltech.habitly.ui.components.HabitlyTextField
import com.monsteraltech.habitly.ui.components.IconHalo
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.theme.habitly

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HabitlyBackground(arrangement = MeshArrangement.Auth) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 26.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                IconHalo(size = 82.dp, cornerRadius = 26.dp) {
                    Icon(
                        imageVector = Icons.Rounded.LockReset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(18.dp).fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = stringResource(R.string.forgot_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.forgot_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.habitly.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 290.dp).padding(bottom = 30.dp)
                )

                HabitlyTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = stringResource(R.string.common_email),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    uiState.isSent -> Text(
                        text = stringResource(R.string.forgot_success),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    uiState.emailInvalid -> Text(
                        text = stringResource(R.string.forgot_error_email),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    uiState.error -> Text(
                        text = stringResource(R.string.forgot_error_generic),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            // --- Parte inferior fija ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HabitlyPrimaryButton(
                    text = stringResource(R.string.forgot_send_link),
                    onClick = viewModel::onSendClick,
                    enabled = !uiState.isSent,
                    loading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                HabitlyTextButton(
                    text = stringResource(R.string.forgot_back_to_login),
                    onClick = onNavigateBack
                )
            }
        }
    }
}
