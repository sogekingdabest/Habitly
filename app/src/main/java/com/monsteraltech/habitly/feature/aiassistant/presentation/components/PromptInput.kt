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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R

/** Chip de sugerencia ya localizado: etiqueta visible + acción al pulsarlo. La pantalla lo
 *  construye resolviendo los textos con `stringResource`, para respetar el idioma de Ajustes. */
data class QuickPromptChip(
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun PromptInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean = false,
    onStop: () -> Unit = {},
    onVoiceInput: (() -> Unit)? = null,
    quickPrompts: List<QuickPromptChip> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (quickPrompts.isNotEmpty() && input.isBlank()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { chip ->
                    AssistChip(
                        onClick = chip.onClick,
                        label = { Text(chip.label, maxLines = 1) }
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
                placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                trailingIcon = if (onVoiceInput != null && input.isBlank()) {
                    {
                        IconButton(onClick = onVoiceInput) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = stringResource(R.string.ai_voice_input)
                            )
                        }
                    }
                } else {
                    null
                }
            )
            if (isGenerating) {
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = stringResource(R.string.ai_stop)
                    )
                }
            } else {
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
}
