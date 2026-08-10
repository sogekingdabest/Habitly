package com.monsteraltech.habitly.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
 * Preferences backed by `SharedPreferences` (named [PREFS_NAME]). It is opened by name inside the
 * impl — not through DI — so it does not clash with the unqualified `SharedPreferences` the
 * assistant module already provides, and so [readLanguageTag] and [readRemindersEnabled] can read
 * the same file synchronously from places without Hilt (`attachBaseContext`, the worker).
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
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    override fun setLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.tag) }
    }

    override fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDERS, enabled) }
    }

    /** Emits the current value and re-emits when [key] (or the whole prefs) changes. */
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

        /** Synchronous read of the language tag (for `attachBaseContext`). "" = system. */
        fun readLanguageTag(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "") ?: ""

        /** Synchronous read of the master reminders switch (for the worker). */
        fun readRemindersEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_REMINDERS, true)
    }
}
