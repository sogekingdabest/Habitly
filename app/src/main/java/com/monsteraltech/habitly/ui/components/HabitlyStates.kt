package com.monsteraltech.habitly.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.Motion
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * What a list shows when it has nothing in it: an icon, what is going on, and — when there is
 * something to do about it — one action.
 *
 * Only three screens had an empty state, and each drew its own. An empty list with no explanation
 * reads as a bug, especially the first time the app is opened.
 */
@Composable
fun HabitlyEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconHalo(size = 64.dp, cornerRadius = 22.dp) {
            Icon(
                icon,
                // Decorative: the title right below says the same thing in words.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.habitly.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * A placeholder block that breathes while the real content loads.
 *
 * It replaces the centred spinner: a spinner says "wait" and then the layout jumps into place,
 * while a skeleton with the shape of what is coming keeps the page still.
 */
@Composable
fun HabitlySkeleton(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp,
    cornerRadius: Dp = 7.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .alpha(alpha)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.habitly.boxIdle),
    )
}

/**
 * The skeleton of a [HabitlyListSection]: the card, and [rows] rows the size of the real ones.
 *
 * [loadingLabel] is what TalkBack announces, since the shapes themselves say nothing; everything
 * inside is hidden from it so it does not read a wall of empty boxes.
 */
@Composable
fun HabitlyListSkeleton(
    loadingLabel: String,
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    HabitlyListSection(
        modifier = modifier.semantics { contentDescription = loadingLabel },
    ) {
        Column(modifier = Modifier.clearAndSetSemantics { }) {
            repeat(rows) { index ->
                if (index > 0) HabitlyRowDivider()
                HabitlyRow(
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .alpha(0.6f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.habitly.boxIdle),
                        )
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HabitlySkeleton(width = if (index % 2 == 0) 170.dp else 130.dp)
                        HabitlySkeleton(width = 90.dp, height = 10.dp, cornerRadius = 5.dp)
                    }
                }
            }
        }
    }
}
