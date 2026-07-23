package com.monsteraltech.habitly.feature.settings.domain.repository

import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Preferencias de la app (tema, idioma, notificaciones). Respaldado por un `SharedPreferences`
 * síncrono para poder leer el idioma en `attachBaseContext`, antes de que Hilt esté disponible.
 * Los setters usan `apply()` (no bloquean) por lo que no necesitan ser `suspend`.
 */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val language: Flow<AppLanguage>
    val remindersEnabled: Flow<Boolean>

    fun setThemeMode(mode: ThemeMode)
    fun setLanguage(language: AppLanguage)
    fun setRemindersEnabled(enabled: Boolean)
}
