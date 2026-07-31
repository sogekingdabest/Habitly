package com.monsteraltech.habitly.feature.settings.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the persisted language to the Activity's `Context`. It is called from
 * `MainActivity.attachBaseContext`, which runs before Hilt injection, which is why it reads the
 * preference synchronously via [SettingsRepositoryImpl.readLanguageTag].
 *
 * Works on API 29+ without `appcompat`: it wraps the context with a `Configuration` whose locale is
 * the chosen one. When the language changes in Settings, an `Activity.recreate()` is enough for this
 * `wrap` to run again with the new locale.
 */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val tag = SettingsRepositoryImpl.readLanguageTag(base)
        if (tag.isBlank()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
