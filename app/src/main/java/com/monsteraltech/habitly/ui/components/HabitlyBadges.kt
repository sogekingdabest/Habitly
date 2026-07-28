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
