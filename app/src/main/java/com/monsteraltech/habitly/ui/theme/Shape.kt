package com.monsteraltech.habitly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom shapes scale for Habitly.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Custom leaf-corner shape with a notched bottom-start corner.
 */
fun leafCornerShape(radius: Dp = 24.dp, notch: Dp = 8.dp) = RoundedCornerShape(
    topStart = radius,
    topEnd = radius,
    bottomEnd = radius,
    bottomStart = notch,
)

/** Large leaf-corner shape for featured cards. */
val LeafCornerLarge = leafCornerShape(28.dp, 8.dp)

/** Standard leaf-corner shape for list item cards. */
val LeafCornerMedium = leafCornerShape(24.dp, 8.dp)
