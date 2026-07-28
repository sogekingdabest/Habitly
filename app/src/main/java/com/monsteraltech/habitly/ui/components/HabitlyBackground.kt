package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.monsteraltech.habitly.ui.theme.habitly
import androidx.compose.material3.MaterialTheme

/**
 * Mesh gradient background — the canvas of every main screen. A misty base colour with soft radial
 * blobs fading to transparent, giving depth without noise under the cream cards.
 *
 * [arrangement] varies the blob positions so adjacent screens do not look identical.
 */
@Composable
fun HabitlyBackground(
    modifier: Modifier = Modifier,
    arrangement: MeshArrangement = MeshArrangement.Home,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.habitly
    val base = colors.meshBase
    val stops = colors.meshStops
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)
                val maxDim = maxOf(size.width, size.height)
                arrangement.blobs.forEachIndexed { i, blob ->
                    val stop = stops[i % stops.size]
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(stop, Color.Transparent),
                            center = Offset(size.width * blob.x, size.height * blob.y),
                            radius = maxDim * blob.radius,
                        )
                    )
                }
            },
        content = content,
    )
}

/** One radial blob: position as a [0..1] fraction, radius as a fraction of the larger dimension. */
data class MeshBlob(val x: Float, val y: Float, val radius: Float)

/** Per-screen blob layouts — same mists, different placement. */
enum class MeshArrangement(val blobs: List<MeshBlob>) {
    Home(
        listOf(
            MeshBlob(0.18f, 0.12f, 0.55f),
            MeshBlob(0.88f, 0.08f, 0.48f),
            MeshBlob(0.78f, 0.72f, 0.52f),
            MeshBlob(0.08f, 0.92f, 0.55f),
            MeshBlob(0.45f, 0.45f, 0.60f),
        )
    ),
    Shopping(
        listOf(
            MeshBlob(0.85f, 0.10f, 0.55f),
            MeshBlob(0.15f, 0.18f, 0.48f),
            MeshBlob(0.20f, 0.80f, 0.52f),
            MeshBlob(0.92f, 0.70f, 0.55f),
            MeshBlob(0.50f, 0.40f, 0.60f),
        )
    ),
    Routines(
        listOf(
            MeshBlob(0.12f, 0.82f, 0.55f),
            MeshBlob(0.82f, 0.88f, 0.48f),
            MeshBlob(0.75f, 0.15f, 0.52f),
            MeshBlob(0.15f, 0.35f, 0.55f),
            MeshBlob(0.55f, 0.55f, 0.60f),
        )
    ),
    Chat(
        listOf(
            MeshBlob(0.25f, 0.22f, 0.55f),
            MeshBlob(0.80f, 0.35f, 0.48f),
            MeshBlob(0.88f, 0.82f, 0.52f),
            MeshBlob(0.12f, 0.78f, 0.55f),
            MeshBlob(0.48f, 0.52f, 0.60f),
        )
    ),
    Household(
        listOf(
            MeshBlob(0.52f, 0.06f, 0.55f),
            MeshBlob(0.08f, 0.55f, 0.48f),
            MeshBlob(0.92f, 0.48f, 0.52f),
            MeshBlob(0.35f, 0.95f, 0.55f),
            MeshBlob(0.60f, 0.30f, 0.60f),
        )
    ),
    Auth(
        listOf(
            MeshBlob(0.40f, 0.12f, 0.55f),
            MeshBlob(0.88f, 0.40f, 0.48f),
            MeshBlob(0.12f, 0.60f, 0.52f),
            MeshBlob(0.70f, 0.92f, 0.55f),
            MeshBlob(0.55f, 0.48f, 0.60f),
        )
    ),
}
