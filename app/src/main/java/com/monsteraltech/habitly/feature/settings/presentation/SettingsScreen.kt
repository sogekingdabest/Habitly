package com.monsteraltech.habitly.feature.settings.presentation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.BuildConfig
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.data.notification.RoutineChannels
import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge

// URLs of the legal pages (GitHub Pages). Must match the Google Play listing.
private const val URL_PRIVACY = "https://sogekingdabest.github.io/habitly-legal/privacidad.html"
private const val URL_TERMS = "https://sogekingdabest.github.io/habitly-legal/terminos.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf("") }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val openLinkError = stringResource(R.string.legal_open_error)
    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, openLinkError, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }
    LaunchedEffect(uiState.deleteAccountError) {
        uiState.deleteAccountError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDeleteAccountErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // === ACCOUNT ===
            SettingsSection(stringResource(R.string.settings_section_account)) {
                SettingsInfoRow(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.settings_email),
                    value = uiState.email.ifBlank { "—" }
                )
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.settings_nickname),
                    value = uiState.nickname.ifBlank { "—" },
                    trailingIcon = Icons.Filled.Edit,
                    onClick = {
                        nicknameInput = uiState.nickname
                        showNicknameDialog = true
                    }
                )
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = stringResource(R.string.household_sign_out),
                    onClick = { showSignOutDialog = true }
                )
            }

            // === APPEARANCE ===
            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                SettingsRowHeader(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_theme)
                )
                ThemeMode.entries.forEach { mode ->
                    SettingsRadioRow(
                        label = stringResource(mode.labelRes()),
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.onThemeModeSelected(mode) }
                    )
                }
            }

            // === LANGUAGE ===
            SettingsSection(stringResource(R.string.settings_section_language)) {
                SettingsRowHeader(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_language)
                )
                AppLanguage.entries.forEach { language ->
                    SettingsRadioRow(
                        label = stringResource(language.labelRes()),
                        selected = uiState.language == language,
                        onClick = {
                            if (uiState.language != language) {
                                viewModel.onLanguageSelected(language)
                                // Recreates the Activity so attachBaseContext applies the locale.
                                activity?.recreate()
                            }
                        }
                    )
                }
            }

            // === NOTIFICATIONS ===
            SettingsSection(stringResource(R.string.settings_section_notifications)) {
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_reminders),
                    subtitle = stringResource(R.string.settings_reminders_desc),
                    checked = uiState.remindersEnabled,
                    onCheckedChange = viewModel::onRemindersToggled
                )
                SettingsDivider()
                // Android freezes a channel's sound and vibration when it is created, so the app
                // cannot offer its own picker. Instead each level links into its own system screen,
                // where the user can set any tone and pattern they like.
                SettingsRowHeader(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_notification_sounds)
                )
                NOTIFICATION_LEVEL_ROWS.forEach { (channelId, labelRes) ->
                    SettingsClickableRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = stringResource(R.string.settings_notification_level_row, stringResource(labelRes)),
                        onClick = {
                            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = stringResource(R.string.settings_open_system_settings),
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            // === ABOUT ===
            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME
                )
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = stringResource(R.string.legal_privacy),
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openUrl(URL_PRIVACY) }
                )
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = stringResource(R.string.legal_terms),
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openUrl(URL_TERMS) }
                )
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.settings_rate),
                    onClick = {
                        val market = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${context.packageName}")
                        )
                        try {
                            context.startActivity(market)
                        } catch (e: ActivityNotFoundException) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                    )
                                )
                            }
                        }
                    }
                )
            }

            // === DANGER ZONE ===
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.household_danger_zone),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.household_delete_account_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                enabled = !uiState.isDeletingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (uiState.isDeletingAccount) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.household_delete_account))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Edit-nickname dialog
    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text(stringResource(R.string.household_change_nickname_title)) },
            text = {
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = { Text(stringResource(R.string.household_short_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onUpdateNickname(nicknameInput)
                        showNicknameDialog = false
                    },
                    enabled = nicknameInput.isNotBlank()
                ) { Text(stringResource(R.string.household_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Log-out dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.household_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.onSignOut(onSignOut)
                }) { Text(stringResource(R.string.household_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Delete-account dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.household_delete_confirm_title)) },
            text = { Text(stringResource(R.string.household_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.onDeleteAccount(onSignOut)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.household_delete_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable components (Cozy skin)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    HabitlyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerLarge,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun SettingsClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRowHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 54.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.SPANISH -> R.string.settings_language_es
    AppLanguage.GALICIAN -> R.string.settings_language_gl
    AppLanguage.ENGLISH -> R.string.settings_language_en
}

/** Reminder levels, each linking into its own system notification-channel screen. */
private val NOTIFICATION_LEVEL_ROWS = listOf(
    RoutineChannels.CHANNEL_SILENT to R.string.routines_level_silent,
    RoutineChannels.CHANNEL_DEFAULT to R.string.routines_level_default,
    RoutineChannels.CHANNEL_HIGH to R.string.routines_level_high
)
