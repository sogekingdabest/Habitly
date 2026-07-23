package com.monsteraltech.habitly.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import com.monsteraltech.habitly.feature.settings.domain.model.AppLanguage
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import com.monsteraltech.habitly.feature.settings.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferencias respaldadas por `SharedPreferences` (nombre [PREFS_NAME]). Se abre por nombre
 * dentro del impl —no vía DI— para no chocar con el `SharedPreferences` sin cualificar que ya
 * provee el módulo del asistente, y para que [readLanguageTag] y [readRemindersEnabled] puedan
 * leer el mismo fichero de forma síncrona desde sitios sin Hilt (`attachBaseContext`, el worker).
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val themeMode: Flow<ThemeMode> =
        observe(KEY_THEME) { ThemeMode.fromName(prefs.getString(KEY_THEME, null)) }

    override val language: Flow<AppLanguage> =
        observe(KEY_LANGUAGE) { AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, "")) }

    override val remindersEnabled: Flow<Boolean> =
        observe(KEY_REMINDERS) { prefs.getBoolean(KEY_REMINDERS, true) }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    override fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    override fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply()
    }

    /** Emite el valor actual y vuelve a emitir cuando cambia [key] (o toda la prefs). */
    private fun <T> observe(key: String, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key || changedKey == null) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    companion object {
        const val PREFS_NAME = "settings_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_REMINDERS = "reminders_enabled"

        /** Lectura síncrona del tag de idioma (para `attachBaseContext`). "" = sistema. */
        fun readLanguageTag(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "") ?: ""

        /** Lectura síncrona del interruptor maestro de recordatorios (para el worker). */
        fun readRemindersEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_REMINDERS, true)
    }
}
