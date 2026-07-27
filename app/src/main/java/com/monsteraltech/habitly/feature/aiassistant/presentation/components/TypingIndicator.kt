package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Tres puntos "respirando" desfasados: el indicador de que el modelo está por empezar a
 * responder, mientras la burbuja aún no tiene texto que enseñar (como el "···" de ChatGPT
 * o Claude). En cuanto llega el primer token, la pantalla lo sustituye por el markdown.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(DOT_COUNT) { index ->
            val alpha by transition.animateFloat(
                initialValue = MIN_ALPHA,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(DOT_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * DOT_STAGGER_MS)
                ),
                label = "typing-dot-$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .alpha(alpha)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}

private const val DOT_COUNT = 3
private const val MIN_ALPHA = 0.3f
private const val DOT_DURATION_MS = 600
private const val DOT_STAGGER_MS = 200
