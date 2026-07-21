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
 * Pastilla cálida con texto — la base de los badges de Habitly. Nunca el chip gris
 * neutro de Material: siempre fondo con color y esquinas totalmente redondeadas.
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

/** Pastilla de racha: 🌱 + número de días encadenados. */
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

/** Pastilla "Te toca" — marca la tarea asignada al usuario actual. */
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
