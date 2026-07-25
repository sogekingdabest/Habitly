package com.monsteraltech.habitly.feature.household.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.HouseholdShareSummary
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.HabitlyPill
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * Panel de reparto de la casa: qué ha hecho cada miembro esta semana, cuántos días seguidos lleva
 * la casa completando todo y cómo fue la semana pasada.
 *
 * **El tono es parte del diseño, no decoración.** En una app que usa gente que convive, un
 * marcador que señale al que menos hace hace daño de verdad. Por eso:
 *  - lo primero y más grande es el total **de la casa**, no el individual,
 *  - las barras van en el orden de los miembros de la casa, nunca de mayor a menor,
 *  - no hay posiciones, ni "peor", ni números en rojo,
 *  - con un solo miembro desaparece toda la comparación y queda solo su progreso.
 *
 * [memberIds] llega en el orden de `Household.members` y [memberNames] puede no traer a alguien
 * que todavía no ha abierto la app: ahí se usa el texto de reserva en vez de una fila en blanco.
 */
@Composable
fun HouseholdSharePanel(
    summary: HouseholdShareSummary,
    memberIds: List<String>,
    memberNames: Map<String, String>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    // Sin rutinas de casa no hay reparto del que hablar.
    if (!summary.hasHouseholdRoutines) return

    val isSolo = memberIds.size <= 1
    val unknownName = stringResource(R.string.household_member_unknown)

    HabitlyCard(
        modifier = modifier.fillMaxWidth(),
        shape = LeafCornerLarge,
        contentPadding = PaddingValues(20.dp)
    ) {
        Text(
            text = stringResource(R.string.household_share_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // El total de la casa manda: es el número que celebra el esfuerzo común.
            Text(
                text = when {
                    summary.thisWeekTotal == 0 -> stringResource(R.string.household_share_empty)
                    isSolo -> pluralStringResource(
                        R.plurals.household_share_total_solo,
                        summary.thisWeekTotal,
                        summary.thisWeekTotal
                    )
                    else -> pluralStringResource(
                        R.plurals.household_share_total,
                        summary.thisWeekTotal,
                        summary.thisWeekTotal
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.habitly.textSecondary
            )

            if (!isSolo && summary.thisWeekTotal > 0) {
                memberIds.forEach { memberId ->
                    MemberShareRow(
                        name = memberNames[memberId]?.takeIf { it.isNotBlank() } ?: unknownName,
                        count = summary.thisWeek[memberId] ?: 0,
                        max = summary.maxThisWeek,
                        isCurrentUser = memberId == currentUserId
                    )
                }
            }

            // Racha de la casa, en la pastilla de racha de la casa (mismos colores que la de
            // cada rutina): es un logro compartido, no una nota individual.
            if (summary.houseStreakDays > 0) {
                HabitlyPill(
                    text = pluralStringResource(
                        R.plurals.household_share_streak,
                        summary.houseStreakDays,
                        summary.houseStreakDays
                    ),
                    background = MaterialTheme.habitly.streakBg,
                    contentColor = MaterialTheme.habitly.streakFg
                )
            }

            // La semana pasada, en una línea y en tono menor: es contexto, no un objetivo.
            if (summary.lastWeekTotal > 0) {
                // El formato se resuelve fuera del bucle: stringResource no puede llamarse
                // dentro de un lambda que no es composable.
                val entryFormat = stringResource(R.string.household_share_last_week_entry)
                val lastWeekText = if (isSolo) {
                    stringResource(R.string.household_share_last_week_total, summary.lastWeekTotal)
                } else {
                    val entries = memberIds
                        .filter { (summary.lastWeek[it] ?: 0) > 0 }
                        .joinToString(" · ") { memberId ->
                            val name = memberNames[memberId]?.takeIf { it.isNotBlank() } ?: unknownName
                            entryFormat.format(name, summary.lastWeek[memberId] ?: 0)
                        }
                    stringResource(R.string.household_share_last_week, entries)
                }
                Text(
                    text = lastWeekText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.habitly.textSecondary
                )
            }
        }
    }
}

/**
 * Una fila del reparto: nombre, barra comparada con el máximo de la semana y recuento.
 *
 * La barra de quien mira lleva el verde de marca y las demás el terciario; es para localizarse
 * de un vistazo, no para marcar quién gana.
 */
@Composable
private fun MemberShareRow(
    name: String,
    count: Int,
    max: Int,
    isCurrentUser: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrentUser) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(92.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.habitly.boxIdle)
        ) {
            val fraction = if (max <= 0) 0f else count.toFloat() / max.toFloat()
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isCurrentUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary
                        )
                )
            }
        }

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(24.dp)
        )
    }
}
