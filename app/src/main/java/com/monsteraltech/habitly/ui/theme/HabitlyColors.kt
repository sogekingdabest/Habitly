package com.monsteraltech.habitly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de marca de Habitly que Material 3 no modela.
 *
 * `MaterialTheme.colorScheme` cubre primario, superficie, error, etc., pero la piel
 * "Cozy Handcrafted" necesita extras: el papel crema de las tarjetas, la sombra verde
 * cálida de la "firma", los colores de las pastillas de racha y "Te toca", y las
 * manchas del mesh gradient del fondo. Se exponen aquí y se leen con
 * `MaterialTheme.habitly` desde cualquier composable bajo [HabitlyTheme].
 */
@Immutable
data class HabitlyColors(
    /** Papel crema de toda superficie-tarjeta (más cálido que `surface`). */
    val card: Color,
    /** Filo de la tarjeta: casi invisible en claro (manda la sombra), filo claro en
     * oscuro donde la sombra negra no separa nada del fondo. */
    val cardBorder: Color,
    /** Sombra teñida en verde cálido — la elevación firma de Habitly. */
    val shadow: Color,
    /** Verde de marca (igual que `primary`, expuesto para dibujar directo). */
    val accent: Color,
    /** Verde profundo para texto sobre contenedores claros. */
    val accentText: Color,
    /** Fondo del halo redondeado de los iconos line-art. */
    val iconHalo: Color,
    /** Fondo de una casilla/toggle sin marcar. */
    val boxIdle: Color,
    /** Borde/contorno suave de campos y tarjetas. */
    val border: Color,
    /** Texto secundario (descripciones, metadatos). */
    val textSecondary: Color,
    /** Iconos y etiquetas de navegación inactivos. */
    val navIdle: Color,
    /** Fondo de la pastilla de racha (🌱). */
    val streakBg: Color,
    /** Texto/emoji de la pastilla de racha. */
    val streakFg: Color,
    /** Fondo de la pastilla "Te toca". */
    val mineBg: Color,
    /** Texto de la pastilla "Te toca". */
    val mineFg: Color,
    /** Color sobre el verde de acento (crema, no blanco puro). */
    val onAccent: Color,
    /** Base y manchas radiales del mesh gradient del fondo. */
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

/** Acceso a los tokens de marca desde cualquier composable bajo `HabitlyTheme`. */
val MaterialTheme.habitly: HabitlyColors
    @Composable
    @ReadOnlyComposable
    get() = LocalHabitlyColors.current
