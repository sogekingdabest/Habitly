package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R

data class QuickPrompt(
    val label: String,
    val prompt: String
)

@Composable
fun PromptInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    quickPrompts: List<QuickPrompt> = emptyList(),
    onQuickPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (quickPrompts.isNotEmpty()) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                androidx.compose.material3.AssistChip(
                    onClick = { onQuickPrompt(prompt.prompt) },
                    label = { Text(prompt.label) }
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.ai_input_hint)) },
            singleLine = false,
            maxLines = 4
        )
        Button(
            onClick = onSend,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.ai_send))
        }
    }
}
