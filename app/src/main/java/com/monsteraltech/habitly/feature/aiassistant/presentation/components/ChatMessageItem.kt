package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatMessageItem(
    message: AiMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role is MessageRole.User

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.padding(
                start = if (isUser) 64.dp else 8.dp,
                end = if (isUser) 8.dp else 64.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        linkColor = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = remember(message.timestamp) { formatTimestamp(message.timestamp) },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTimestamp(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
}
