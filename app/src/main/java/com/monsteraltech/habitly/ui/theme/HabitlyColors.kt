package com.monsteraltech.habitly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Habitly brand tokens that Material 3 does not model: the cream card paper, the warm green
 * signature shadow, the streak and "your turn" pill colours, and the background mesh gradient.
 * Read them via `MaterialTheme.habitly` from any composable under [HabitlyTheme].
 */
@Immutable
data class HabitlyColors(
    /** Cream paper of every card surface (warmer than `surface`). */
    val card: Color,
    /** Card edge: nearly invisible in light (the shadow does the work), a light hairline in dark
     * where a black shadow separates nothing from the background. */
    val cardBorder: Color,
    /** Warm green tinted shadow — Habitly's signature elevation. */
    val shadow: Color,
    /** Brand green (same as `primary`, exposed for direct drawing). */
    val accent: Color,
    /** Deep green for text on light containers. */
    val accentText: Color,
    /** Background of the rounded halo behind line-art icons. */
    val iconHalo: Color,
    /** Background of an unchecked checkbox/toggle. */
    val boxIdle: Color,
    /** Soft border/outline of fields and cards. */
    val border: Color,
    /** Secondary text (descriptions, metadata). */
    val textSecondary: Color,
    /** Inactive navigation icons and labels. */
    val navIdle: Color,
    /** Background of the streak pill (🌱). */
    val streakBg: Color,
    /** Text/emoji of the streak pill. */
    val streakFg: Color,
    /** Background of the "your turn" pill. */
    val mineBg: Color,
    /** Text of the "your turn" pill. */
    val mineFg: Color,
    /** Colour on top of the accent green (cream, not pure white). */
    val onAccent: Color,
    /** Base and radial blobs of the background mesh gradient. */
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

/** Access to the brand tokens from any composable under `HabitlyTheme`. */
val MaterialTheme.habitly: HabitlyColors
    @Composable
    @ReadOnlyComposable
    get() = LocalHabitlyColors.current
