package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType

/** Cuántas rutinas se listan antes de resumir el resto en una línea. */
private const val MAX_PREVIEW_ROUTINES = 4

/**
 * Tarjeta que aparece bajo un mensaje del asistente cuando este ha propuesto rutinas.
 * Deja elegir si van a las personales o a las de la casa antes de crearlas.
 */
@Composable
fun RoutineSuggestionCard(
    routines: List<AiRoutineSuggestion>,
    isAdded: Boolean,
    isLoading: Boolean,
    onAdd: (RoutineType) -> Unit,
    modifier: Modifier = Modifier
) {
    var type by remember { mutableStateOf(RoutineType.PERSONAL) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 32.dp, top = 2.dp, bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.EventRepeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = pluralStringResource(R.plurals.ai_routine_count, routines.size, routines.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            // Un vistazo a lo que se va a crear: crear cosas a ciegas da mal cuerpo. Se recorta
            // la lista para que una tanda larga no convierta la tarjeta en media pantalla.
            routines.take(MAX_PREVIEW_ROUTINES).forEach { routine ->
                Text(
                    text = "• ${routine.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            if (routines.size > MAX_PREVIEW_ROUTINES) {
                Text(
                    text = stringResource(
                        R.string.ai_routine_more,
                        routines.size - MAX_PREVIEW_ROUTINES
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            if (isAdded) {
                SuggestionDoneLabel(
                    text = stringResource(R.string.ai_routine_added),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.align(Alignment.End)
                )
            } else {
                // FlowRow y no Row: con fuente grande los dos chips no caben en el ancho
                // de la tarjeta y tienen que poder pasar a la línea siguiente.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineTypeChip(
                        selected = type == RoutineType.PERSONAL,
                        onClick = { type = RoutineType.PERSONAL },
                        label = stringResource(R.string.routines_type_personal)
                    )
                    RoutineTypeChip(
                        selected = type == RoutineType.HOUSEHOLD,
                        onClick = { type = RoutineType.HOUSEHOLD },
                        label = stringResource(R.string.routines_type_household)
                    )
                }

                Button(
                    onClick = { onAdd(type) },
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                    } else {
                        Text(stringResource(R.string.ai_routine_add), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Chip de destino de las rutinas. Lleva colores propios porque los del tema salen de la
 * paleta secundaria y sobre el fondo terciario de la tarjeta el estado activo se confundía
 * con el inactivo; el check deja la selección clara aunque el color no se aprecie.
 */
@Composable
private fun RoutineTypeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.tertiaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.4f)
        )
    )
}
