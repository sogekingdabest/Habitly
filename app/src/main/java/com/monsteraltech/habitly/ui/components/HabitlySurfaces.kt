package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * Habitly's signature shadow: warm green tinted instead of Material's grey. The tint is used at
 * full opacity as ambient and spot colour; [elevation] controls the blur.
 */
@Composable
fun Modifier.habitlyShadow(elevation: Dp, shape: Shape): Modifier {
    val tint = MaterialTheme.habitly.shadow.copy(alpha = 1f)
    return this.shadow(elevation = elevation, shape = shape, ambientColor = tint, spotColor = tint)
}

/**
 * Habitly's signature card: cream paper, leaf corner and warm shadow. Replaces Material's `Card`
 * keeping its role as a surface container. Passing [onClick] makes it clickable.
 */
@Composable
fun HabitlyCard(
    modifier: Modifier = Modifier,
    shape: Shape = LeafCornerLarge,
    color: Color = MaterialTheme.habitly.card,
    elevation: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .habitlyShadow(elevation, shape)
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.habitly.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Toggleable variant of [HabitlyCard] for checkable rows (routines, products). Exposes a checkbox
 * role for accessibility; the content draws the state.
 */
@Composable
fun HabitlyToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = LeafCornerLarge,
    color: Color = MaterialTheme.habitly.card,
    elevation: Dp = 10.dp,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .habitlyShadow(elevation, shape)
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.habitly.cardBorder, shape)
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Rounded halo behind a line-art icon — the icon container shape of the set. */
@Composable
fun IconHalo(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 16.dp,
    background: Color = MaterialTheme.habitly.iconHalo,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
