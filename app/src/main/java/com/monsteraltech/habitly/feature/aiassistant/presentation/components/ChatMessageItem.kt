package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.monsteraltech.habitly.feature.aiassistant.domain.util.AiStructuredBlocks
import com.monsteraltech.habitly.feature.aiassistant.domain.util.MarkdownTableSegments
import com.monsteraltech.habitly.ui.theme.habitly
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
            // Sin margen en el lado propio: la burbuja apura hasta el borde (el aire que
            // queda lo pone el contentPadding de la lista). Enfrente queda solo el offset
            // justo para leer de qué lado viene cada mensaje: al asistente se le da casi
            // todo el ancho porque es quien manda tablas y listas largas.
            modifier = Modifier.padding(
                start = if (isUser) 32.dp else 0.dp,
                end = if (isUser) 0.dp else 16.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.habitly.card
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
                    val segments = remember(message.content) {
                        MarkdownTableSegments.split(
                            AiStructuredBlocks.stripFromDisplay(message.content)
                        )
                    }
                    val markdownStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    segments.forEach { segment ->
                        when (segment) {
                            is MarkdownTableSegments.Segment.Text -> MarkdownText(
                                markdown = segment.content,
                                style = markdownStyle,
                                linkColor = MaterialTheme.colorScheme.primary
                            )

                            is MarkdownTableSegments.Segment.Table -> AssistantTable(
                                segment = segment,
                                style = markdownStyle
                            )
                        }
                    }
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

/**
 * Tabla del asistente a su ancho natural con scroll horizontal (como ChatGPT o Gemini):
 * mejor deslizar que estrujar cuatro columnas en el ancho de un móvil partiendo palabras.
 */
@Composable
private fun AssistantTable(
    segment: MarkdownTableSegments.Segment.Table,
    style: androidx.compose.ui.text.TextStyle
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val naturalWidth = (segment.columnCount * TABLE_COLUMN_WIDTH_DP).dp
        val tableWidth = maxOf(maxWidth, naturalWidth)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            MarkdownText(
                markdown = segment.content,
                style = style,
                modifier = Modifier.width(tableWidth)
            )
        }
    }
}

/** Ancho holgado por columna; con él las celdas respiran y el sobrante se desliza. */
private const val TABLE_COLUMN_WIDTH_DP = 140

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTimestamp(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
}
