package com.monsteraltech.habitly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes that repeat across screens. Like [Spacing], these are the values every screen used to
 * improvise: a row was 48dp here and 60dp there, an avatar 36dp or 40dp depending on the file.
 *
 * Usage: `Modifier.heightIn(min = MaterialTheme.sizing.rowMin)`
 */
@Immutable
data class Sizing(
    /** 56dp — a list row with a title and one line of metadata. */
    val rowMin: Dp = 56.dp,
    /** 48dp — a row with a single line, and the floor for anything touchable. */
    val rowCompact: Dp = 48.dp,
    /** 48dp — minimum touch target, whatever the icon inside measures. */
    val touchTarget: Dp = 48.dp,
    /** 20dp — icon inside a row or a chip. */
    val iconSmall: Dp = 20.dp,
    /** 24dp — icon of an action in a top bar. */
    val icon: Dp = 24.dp,
    /** 28dp — avatar inside a row. */
    val avatarSmall: Dp = 28.dp,
    /** 38dp — avatar of a member in a list. */
    val avatar: Dp = 38.dp,
    /** 600dp — content stops growing here and centres, so a tablet is not one endless row. */
    val contentMaxWidth: Dp = 600.dp,
    /** Blur of the warm shadow, by how lifted the surface is. */
    val elevationRaised: Dp = 12.dp,
    val elevationFloating: Dp = 16.dp,
)

/** Durations of the three motions the app uses. Anything else is an exception worth a comment. */
object Motion {
    /** A state that flips: a toggle, a chip, a colour. */
    const val QUICK_MILLIS = 150
    /** Something that grows, folds or moves inside the screen. */
    const val STANDARD_MILLIS = 250
    /** Something that arrives: a sheet, a screen, a list. */
    const val ENTER_MILLIS = 350
}

val LocalSizing = staticCompositionLocalOf { Sizing() }

/** Access to the sizing scale from any composable under `HabitlyTheme`. */
val MaterialTheme.sizing: Sizing
    @Composable
    @ReadOnlyComposable
    get() = LocalSizing.current
