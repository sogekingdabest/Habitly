package com.monsteraltech.habitly.feature.settings.presentation

import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode

data class SettingsUiState(
    val email: String = "",
    val nickname: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val remindersEnabled: Boolean = true,
    val isDeletingAccount: Boolean = false,
    val deleteAccountError: String? = null,
    val error: String? = null,
)
