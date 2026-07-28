package com.monsteraltech.habitly.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * The circle that replaces Material's square Checkbox: a soft-bordered ring when empty, filled
 * green with a hand-drawn tick when done.
 *
 * **Visual only** — the containing row or card provides the `toggleable` and the accessibility
 * role, which avoids nested clickables.
 */
@Composable
fun RitualToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val habitly = MaterialTheme.habitly
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    // The tick draws in progressively when checked — a small "hand-made" gesture.
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "ritualCheck",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(primary)
                else Modifier.background(habitly.boxIdle).border(2.dp, habitly.border, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            Canvas(modifier = Modifier.size(size * 0.52f)) {
                val w = this.size.width
                val h = this.size.height
                val stroke = w * 0.16f
                // Tick stroke: down to the valley, up to the peak, with a rounded corner.
                val p1 = Offset(w * 0.10f, h * 0.54f)
                val p2 = Offset(w * 0.42f, h * 0.82f)
                val p3 = Offset(w * 0.90f, h * 0.24f)
                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    // First segment complete, second segment revealed by [progress].
                    val firstEnd = 0.45f
                    if (progress <= firstEnd) {
                        val t = progress / firstEnd
                        lineTo(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                    } else {
                        lineTo(p2.x, p2.y)
                        val t = (progress - firstEnd) / (1f - firstEnd)
                        lineTo(p2.x + (p3.x - p2.x) * t, p2.y + (p3.y - p2.y) * t)
                    }
                }
                drawPath(
                    path = path,
                    color = onPrimary,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
