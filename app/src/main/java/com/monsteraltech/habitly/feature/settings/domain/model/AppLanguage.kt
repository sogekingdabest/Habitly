package com.monsteraltech.habitly.feature.settings.domain.model

/**
 * The app language. [SYSTEM] (empty tag) follows the device language; the rest force a specific
 * locale, applied in `attachBaseContext`. The [tag] is a valid BCP-47 for `Locale.forLanguageTag`.
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
