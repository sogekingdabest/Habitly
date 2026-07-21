package com.monsteraltech.habitly.feature.routines.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendario mensual de cumplimiento de una rutina.
 *
 * Se pinta a partir de la subcolección `completions`, que ya existía: cada día completado es
 * un documento con id `yyyy-MM-dd`.
 *
 * Accesibilidad: la cuadrícula es decorativa a propósito. Leer 31 celdas con TalkBack sería
 * ruido; la información real (racha actual, mejor racha y cumplimiento) va como texto justo
 * encima, en [com.monsteraltech.habitly.feature.routines.presentation.RoutineDetailSheet].
 */
@Composable
fun CompletionHeatmap(
    month: YearMonth,
    completedDates: Set<LocalDate>,
    isDueOn: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now()
) {
    val firstDay = month.atDay(1)
    // java.time numera lunes=1: los huecos previos alinean el día 1 con su columna.
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val cells = leadingBlanks + daysInMonth
    val weeks = (cells + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WEEKDAY_LABELS.forEach { res ->
                Box(modifier = Modifier.size(CELL_SIZE), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        repeat(weeks) { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(DAYS_PER_WEEK) { dayOfWeek ->
                    val dayNumber = week * DAYS_PER_WEEK + dayOfWeek - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        DayCell(
                            date = month.atDay(dayNumber),
                            completedDates = completedDates,
                            isDueOn = isDueOn,
                            today = today
                        )
                    } else {
                        Box(modifier = Modifier.size(CELL_SIZE))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    completedDates: Set<LocalDate>,
    isDueOn: (LocalDate) -> Boolean,
    today: LocalDate
) {
    val isCompleted = date in completedDates
    val isFuture = date.isAfter(today)
    // Solo marcamos como fallado lo que ya pasó: hoy sigue siendo recuperable.
    val isMissed = !isCompleted && !isFuture && date != today && isDueOn(date)

    val background = when {
        isCompleted -> MaterialTheme.colorScheme.primary
        isMissed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isCompleted -> MaterialTheme.colorScheme.onPrimary
        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val todayRing = if (date == today) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(CircleShape)
            .background(background)
            .then(todayRing),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(1.dp)
        )
    }
}

private const val DAYS_PER_WEEK = 7
private val CELL_SIZE = 36.dp

/** De lunes a domingo, en el mismo orden en que `java.time` numera los días. */
private val WEEKDAY_LABELS = listOf(
    R.string.routines_day_mon,
    R.string.routines_day_tue,
    R.string.routines_day_wed,
    R.string.routines_day_thu,
    R.string.routines_day_fri,
    R.string.routines_day_sat,
    R.string.routines_day_sun
)
