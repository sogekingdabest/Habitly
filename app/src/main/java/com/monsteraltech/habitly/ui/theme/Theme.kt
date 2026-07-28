package com.monsteraltech.habitly.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light scheme: brand green as primary, cream paper surfaces, soft mists for containers. The warm
 * badges (streak, mustard) live in `tertiary` and in [HabitlyColors].
 */
private val LightColorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Cream,
    primaryContainer = SageSoft,
    onPrimaryContainer = SageHover,
    inversePrimary = Sage,

    secondary = SageText,
    onSecondary = Cream,
    secondaryContainer = SageMist,
    onSecondaryContainer = SageDark,

    tertiary = StreakFg,
    onTertiary = Cream,
    tertiaryContainer = StreakBg,
    onTertiaryContainer = Color(0xFF6E4A11),

    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,

    background = MeshBase,
    onBackground = InkPrimary,
    surface = Cream,
    onSurface = InkPrimary,
    surfaceVariant = BoxIdle,
    onSurfaceVariant = InkSecondary,
    surfaceTint = Sage,
    inverseSurface = InkPrimary,
    inverseOnSurface = Cream,

    surfaceDim = Color(0xFFDDE6E0),
    surfaceBright = Cream,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Cream,
    surfaceContainer = Color(0xFFF1F6F1),
    surfaceContainerHigh = MeshBase,
    surfaceContainerHighest = Color(0xFFE4EDE7),

    outline = InkMuted,
    outlineVariant = MistBorder,
    scrim = Color.Black
)

/** Dark scheme — the night variant of the same palette. */
private val DarkColorScheme = darkColorScheme(
    primary = SageDarkPrimary,          // #74a596
    onPrimary = SageDarkOnPrimary,      // #f9fbf6
    primaryContainer = SageDarkContainer, // #2a3b33
    onPrimaryContainer = SageDarkAccent,  // #88ceba
    inversePrimary = Sage,

    secondary = SageDarkAccent,         // #88ceba — acento luminoso
    onSecondary = InkDarkBg,
    secondaryContainer = SageDarkNavActive, // #31473d — pastilla de pestaña activa
    onSecondaryContainer = InkDarkOnSurface, // #eaf2ed

    tertiary = StreakFgDark,            // #e0ac4c — mostaza cálido
    onTertiary = Color(0xFF3F2E08),
    tertiaryContainer = StreakBgDark,   // #3e3417
    onTertiaryContainer = StreakFgDark,

    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,

    background = InkDarkBg,             // #0f1915
    onBackground = InkDarkOnSurface,   // #eaf2ed
    surface = InkDarkSurface,          // #20302a — el "papel" oscuro
    onSurface = InkDarkOnSurface,      // #eaf2ed
    surfaceVariant = InkDarkVariant,   // #2a3b33
    onSurfaceVariant = InkDarkSecondary, // #9db2a8
    surfaceTint = SageDarkPrimary,
    inverseSurface = InkDarkOnSurface,
    inverseOnSurface = InkDarkBg,

    surfaceDim = InkDarkBg,            // #0f1915
    surfaceBright = Color(0xFF2E3F37),
    surfaceContainerLowest = InkDarkCanvas, // #0a100d
    surfaceContainerLow = InkDarkSurface,   // #20302a
    surfaceContainer = Color(0xFF243530),
    surfaceContainerHigh = Color(0xFF2A3B34),
    surfaceContainerHighest = Color(0xFF31433B),

    outline = InkDarkMuted,            // #778a81
    outlineVariant = InkDarkBorder,    // #33443c
    scrim = Color.Black
)

@Composable
fun HabitlyTheme(
    darkTheme: Boolean? = null,
    // Dynamic colour breaks the brand identity, so it is off by default. The parameter stays in
    // case it is ever offered as a user option.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val habitlyColors = if (isDark) DarkHabitlyColors else LightHabitlyColors

    // Transparent system bars with icons that contrast against the background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val lightBars = colorScheme.background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalHabitlyColors provides habitlyColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
