package com.monsteraltech.habitly.feature.settings.domain.model

/**
 * The theme mode the user chose. [SYSTEM] defers to the system setting; the other two force light
 * or dark. It maps to the `darkTheme: Boolean?` flag `HabitlyTheme` consumes.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** `null` = follow the system; `false` = light; `true` = dark. */
    fun toDarkOverride(): Boolean? = when (this) {
        SYSTEM -> null
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
