package com.monsteraltech.habitly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Habitly's shape scale — rounder than the Material 3 default. Material components consume it on
 * their own: `Card` uses [Shapes.medium], dialogs and `BottomSheet` use [Shapes.extraLarge].
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Habitly's signature leaf corner: every card surface clips its bottom-left corner like a folded
 * leaf while keeping the other three rounded. That detail plus the warm shadow is what makes the
 * app recognisable at a glance.
 *
 * @param radius radius of the three rounded corners.
 * @param notch  small radius of the "folded" bottom-left corner.
 */
fun leafCornerShape(radius: Dp = 24.dp, notch: Dp = 8.dp) = RoundedCornerShape(
    topStart = radius,
    topEnd = radius,
    bottomEnd = radius,
    bottomStart = notch,
)

/** Large leaf corner for highlighted cards (header, shopping). */
val LeafCornerLarge = leafCornerShape(28.dp, 8.dp)

/** Standard leaf corner for list rows (routines, products). */
val LeafCornerMedium = leafCornerShape(24.dp, 8.dp)
