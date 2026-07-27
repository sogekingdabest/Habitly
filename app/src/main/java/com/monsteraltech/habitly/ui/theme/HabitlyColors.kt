package com.monsteraltech.habitly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Custom brand tokens for Habitly UI not modeled by standard Material 3.
 * Accessible via `MaterialTheme.habitly` under [HabitlyTheme].
 */
@Immutable
data class HabitlyColors(
    /** Card background color. */
    val card: Color,
    /** Card border stroke color. */
    val cardBorder: Color,
    /** Warm tinted elevation shadow. */
    val shadow: Color,
    /** Brand accent color. */
    val accent: Color,
    /** Deep accent text for light containers. */
    val accentText: Color,
    /** Background halo for line-art icons. */
    val iconHalo: Color,
    /** Unchecked toggle/checkbox background. */
    val boxIdle: Color,
    /** Border stroke for fields and cards. */
    val border: Color,
    /** Secondary text (descriptions, metadata). */
    val textSecondary: Color,
    /** Inactive navigation icons/labels. */
    val navIdle: Color,
    /** Streak pill background. */
    val streakBg: Color,
    /** Streak pill foreground text. */
    val streakFg: Color,
    /** Assigned-to-me pill background. */
    val mineBg: Color,
    /** Assigned-to-me pill foreground text. */
    val mineFg: Color,
    /** Text color on accent background. */
    val onAccent: Color,
    /** Background base and gradient stops. */
    val meshBase: Color,
    val meshStops: List<Color>,
)

val LightHabitlyColors = HabitlyColors(
    card = Cream,
    cardBorder = Color.Transparent,
    shadow = WarmShadow,
    accent = Sage,
    accentText = SageText,
    iconHalo = SageSoft,
    boxIdle = BoxIdle,
    border = MistBorder,
    textSecondary = InkSecondary,
    navIdle = InkMuted,
    streakBg = StreakBg,
    streakFg = StreakFg,
    mineBg = SageSoft,
    mineFg = SageDark,
    onAccent = Cream,
    meshBase = MeshBase,
    meshStops = listOf(Mesh1, Mesh2, Mesh3, Mesh4, Mesh5),
)

val DarkHabitlyColors = HabitlyColors(
    card = InkDarkSurface,          // #20302a
    cardBorder = InkDarkHairline,   // blanco ~10% — separa la tarjeta del fondo
    shadow = WarmShadowDark,
    accent = SageDarkPrimary,       // #74a596
    accentText = SageDarkAccent,    // #88ceba — enlaces y botones de texto luminosos
    iconHalo = SageDarkContainer,   // #2a3b33
    boxIdle = InkDarkBoxIdle,       // #16211c
    border = InkDarkBorder,         // #33443c
    textSecondary = InkDarkSecondary, // #9db2a8
    navIdle = InkDarkMuted,         // #778a81
    streakBg = StreakBgDark,        // #3e3417
    streakFg = StreakFgDark,        // #e0ac4c
    mineBg = SageDarkContainer,     // #2a3b33
    mineFg = SageDarkPrimaryDark,   // #6ba491
    onAccent = SageDarkOnPrimary,   // #f9fbf6
    meshBase = InkDarkBg,           // #0f1915
    meshStops = listOf(MeshDark1, MeshDark2, MeshDark3, MeshDark4, MeshDark5),
)

val LocalHabitlyColors = staticCompositionLocalOf { LightHabitlyColors }

/** Access to brand tokens from any composable under `HabitlyTheme`. */
val MaterialTheme.habitly: HabitlyColors
    @Composable
    @ReadOnlyComposable
    get() = LocalHabitlyColors.current
