package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import com.monsteraltech.habitly.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake determinista de [SettingsRepository]. Por defecto el idioma es español para que los
 * tests del contexto del asistente no dependan de la locale de la máquina que corre los tests.
 */
class FakeSettingsRepository(
    var stubLanguage: AppLanguage = AppLanguage.SPANISH,
    var stubThemeMode: ThemeMode = ThemeMode.SYSTEM,
    var stubRemindersEnabled: Boolean = true
) : SettingsRepository {

    override val themeMode: Flow<ThemeMode> get() = flowOf(stubThemeMode)
    override val language: Flow<AppLanguage> get() = flowOf(stubLanguage)
    override val remindersEnabled: Flow<Boolean> get() = flowOf(stubRemindersEnabled)

    override fun setThemeMode(mode: ThemeMode) { stubThemeMode = mode }
    override fun setLanguage(language: AppLanguage) { stubLanguage = language }
    override fun setRemindersEnabled(enabled: Boolean) { stubRemindersEnabled = enabled }

    fun reset() {
        stubLanguage = AppLanguage.SPANISH
        stubThemeMode = ThemeMode.SYSTEM
        stubRemindersEnabled = true
    }
}
