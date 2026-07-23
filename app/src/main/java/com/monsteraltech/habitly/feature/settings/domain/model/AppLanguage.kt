package com.monsteraltech.habitly.feature.settings.domain.model

/**
 * Idioma de la app. [SYSTEM] (tag vacío) sigue el idioma del dispositivo; el resto fuerzan
 * una locale concreta que se aplica en `attachBaseContext`. El [tag] es un BCP-47 válido
 * para `Locale.forLanguageTag`.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    SPANISH("es"),
    GALICIAN("gl"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
