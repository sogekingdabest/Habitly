package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiQuickPrompt

@Composable
fun PromptInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    quickPrompts: List<AiQuickPrompt> = emptyList(),
    onQuickPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Los chips se van en cuanto empiezas a escribir: ya has decidido qué preguntar, y esa
        // fila es altura que le hace más falta a la conversación.
        if (quickPrompts.isNotEmpty() && input.isBlank()) {
            // El padding va en el contenido y no en la fila para que los chips se deslicen
            // hasta el borde de la pantalla en vez de cortarse en seco a 16dp.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { prompt ->
                    AssistChip(
                        onClick = { onQuickPrompt(prompt.prompt) },
                        label = { Text(prompt.label, maxLines = 1) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                // Placeholder y no label: el label flotante reserva altura permanente para
                // repetir algo que en un chat ya se da por sabido.
                placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            FilledIconButton(
                onClick = onSend,
                enabled = input.isNotBlank(),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.ai_send)
                )
            }
        }
    }
}
