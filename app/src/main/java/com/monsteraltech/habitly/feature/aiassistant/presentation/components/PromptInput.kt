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

/** An already-localised suggestion chip: visible label plus its tap action. The screen builds it by
 *  resolving the texts with `stringResource`, so it honours the language set in Settings. */
data class QuickPromptChip(
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun PromptInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isGenerating: Boolean = false,
    onStop: () -> Unit = {},
    onVoiceInput: (() -> Unit)? = null,
    quickPrompts: List<QuickPromptChip> = emptyList()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The chips leave as soon as you start typing: you have already decided what to ask, and
        // that row is height the conversation needs more.
        if (quickPrompts.isNotEmpty() && input.isBlank()) {
            // Padding on the content rather than the row, so the chips scroll all the way to the
            // screen edge instead of being cut dead at 16dp.
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
                // Placeholder, not label: a floating label permanently reserves height to repeat
                // something a chat already makes obvious.
                placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                // The mic withdraws once there is text: that space belongs to the content.
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
            // While the model generates, the button becomes "stop": sending no longer applies (the
            // ViewModel ignores it) and this way there is always a useful action available.
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
