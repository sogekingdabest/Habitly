package com.monsteraltech.habitly.feature.settings.domain.repository

import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * App preferences (theme, language, notifications). Backed by a synchronous `SharedPreferences` so
 * the language can be read in `attachBaseContext`, before Hilt is available. The setters use
 * `apply()` (non-blocking), so they need not be `suspend`.
 */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val language: Flow<AppLanguage>
    val remindersEnabled: Flow<Boolean>

    fun setThemeMode(mode: ThemeMode)
    fun setLanguage(language: AppLanguage)
    fun setRemindersEnabled(enabled: Boolean)
}
