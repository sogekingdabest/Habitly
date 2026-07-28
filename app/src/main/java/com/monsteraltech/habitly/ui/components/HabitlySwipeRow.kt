package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Swipeable row. Swiping **right** runs the primary action (tick, complete) and the row springs
 * back; swiping **left** deletes. With one hand busy, a swipe is faster and more accurate than
 * hitting a 20dp icon.
 *
 * The gesture is never the only route: always pair it with [swipeRowSemantics] on the inner row
 * so TalkBack offers the same actions.
 *
 * @param dismissOnDelete whether the row stays off-screen after the delete gesture. Set `false`
 *   when deletion opens a confirmation dialog — the row must survive until the user confirms.
 */
@Composable
fun HabitlySwipeRow(
    onPrimaryAction: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector = Icons.Outlined.Check,
    dismissOnDelete: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection, primaryIcon) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrimaryAction()
                    // The row does not disappear, it only changes state: send it back.
                    scope.launch { state.reset() }
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    if (!dismissOnDelete) scope.launch { state.reset() }
                }

                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        content = content,
    )
}

/**
 * Screen-reader equivalents of the swipe gestures.
 *
 * Must be applied to the **same node** that carries the row's `toggleable`/`clickable`: that is
 * the node TalkBack focuses, and custom actions are only offered on the focused node.
 */
fun Modifier.swipeRowSemantics(
    primaryLabel: String,
    onPrimaryAction: () -> Unit,
    deleteLabel: String,
    onDelete: () -> Unit,
): Modifier = semantics {
    customActions = listOf(
        CustomAccessibilityAction(primaryLabel) { onPrimaryAction(); true },
        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
    )
}

/** Background revealed by the swipe: green with a tick to the right, red with a bin to the left. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, primaryIcon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    val (background, tint, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            SwipeBackgroundSpec(scheme.primaryContainer, scheme.onPrimaryContainer, primaryIcon, Alignment.CenterStart)

        SwipeToDismissBoxValue.EndToStart ->
            SwipeBackgroundSpec(scheme.errorContainer, scheme.onErrorContainer, Icons.Outlined.Delete, Alignment.CenterEnd)

        SwipeToDismissBoxValue.Settled ->
            SwipeBackgroundSpec(Color.Transparent, Color.Transparent, primaryIcon, Alignment.CenterStart)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            // Decorative: swipeRowSemantics already announces what the action does.
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

private data class SwipeBackgroundSpec(
    val background: Color,
    val tint: Color,
    val icon: ImageVector,
    val alignment: Alignment,
)
