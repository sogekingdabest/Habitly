package com.monsteraltech.habitly.feature.settings.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Aplica el idioma persistido al `Context` de la Activity. Se llama desde
 * `MainActivity.attachBaseContext`, que corre antes de la inyección de Hilt, por eso lee la
 * preferencia de forma síncrona con [SettingsRepositoryImpl.readLanguageTag].
 *
 * Funciona en API 29+ sin `appcompat`: envuelve el contexto con una `Configuration` cuya locale
 * es la elegida. Al cambiar el idioma en Ajustes basta con `Activity.recreate()` para que este
 * `wrap` se ejecute de nuevo con la nueva locale.
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
