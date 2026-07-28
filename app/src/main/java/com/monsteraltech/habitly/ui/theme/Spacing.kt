package com.monsteraltech.habitly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Habitly's spacing scale, in 4dp steps.
 *
 * Material 3 defines no spacing tokens, so every screen improvised its own paddings. Using this
 * scale instead of loose values is what keeps the vertical rhythm consistent across screens.
 *
 * Usage: `Modifier.padding(MaterialTheme.spacing.md)`
 */
@Immutable
data class Spacing(
    /** 4dp — between an icon and its label. */
    val xs: Dp = 4.dp,
    /** 8dp — between items in the same row or group. */
    val sm: Dp = 8.dp,
    /** 16dp — screen side margin and gap between cards. */
    val md: Dp = 16.dp,
    /** 24dp — between sections within a screen. */
    val lg: Dp = 24.dp,
    /** 32dp — breathing room around highlighted blocks or empty states. */
    val xl: Dp = 32.dp,
    /** 48dp — between main blocks on onboarding screens. */
    val xxl: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Access to the spacing scale from any composable under `HabitlyTheme`. */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
