package com.monsteraltech.habitly.feature.settings.domain.model

/**
 * Modo de tema elegido por el usuario. [SYSTEM] delega en el ajuste del sistema; los otros
 * dos fuerzan claro u oscuro. Se traduce a la bandera `darkTheme: Boolean?` que consume
 * `HabitlyTheme`.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** `null` = seguir al sistema; `false` = claro; `true` = oscuro. */
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
