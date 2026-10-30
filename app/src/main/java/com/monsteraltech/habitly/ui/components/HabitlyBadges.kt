package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * Warm text pill — the base of every Habitly badge. Never Material's neutral grey chip: always a
 * coloured background with fully rounded corners.
 */
@Composable
fun HabitlyPill(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

/** Streak pill: 🌱 + number of consecutive days. */
@Composable
fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
    val habitly = MaterialTheme.habitly
    HabitlyPill(
        text = "🌱 $streak",
        background = habitly.streakBg,
        contentColor = habitly.streakFg,
        modifier = modifier,
    )
}

/**
 * Metadata chip for the second line of a row: an optional icon plus a short label.
 *
 * At most two per row. A routine knows eight things about itself — description, who did it,
 * paused, whose turn, streak, protected streak, comments, start and end date — and used to say
 * all of them stacked with the same weight; the rest belongs in its detail sheet.
 */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: MetaChipTone = MetaChipTone.NEUTRAL,
) {
    val habitly = MaterialTheme.habitly
    val background = when (tone) {
        MetaChipTone.NEUTRAL -> habitly.boxIdle
        MetaChipTone.ACCENT -> habitly.mineBg
        MetaChipTone.WARM -> habitly.streakBg
    }
    val contentColor = when (tone) {
        MetaChipTone.NEUTRAL -> habitly.textSecondary
        MetaChipTone.ACCENT -> habitly.mineFg
        MetaChipTone.WARM -> habitly.streakFg
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

/** The three tones a [MetaChip] can take: plain, "this concerns you", and streak. */
enum class MetaChipTone { NEUTRAL, ACCENT, WARM }

/** "Your turn" pill — marks the task assigned to the current user. */
@Composable
fun MineBadge(text: String, modifier: Modifier = Modifier) {
    val habitly = MaterialTheme.habitly
    HabitlyPill(
        text = text,
        background = habitly.mineBg,
        contentColor = habitly.mineFg,
        modifier = modifier,
    )
}
