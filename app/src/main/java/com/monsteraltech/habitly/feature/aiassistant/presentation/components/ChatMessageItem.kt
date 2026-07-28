package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
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
    modifier: Modifier = Modifier,
    /** Assistant's last message still generating: no copy button, and dots while it stays empty. */
    isStreaming: Boolean = false,
    onCopy: (String) -> Unit = {},
    /** Already reported: hides the report action so nothing is sent twice. */
    isReported: Boolean = false,
    onReport: () -> Unit = {}
) {
    val isUser = message.role is MessageRole.User
    // Text shown and copied: the user's verbatim, the assistant's without its @@…@@ structured
    // block, which is metadata the UI hides.
    val displayText = if (isUser) message.content
        else AiStructuredBlocks.stripFromDisplay(message.content)
    val isAwaitingFirstToken = isStreaming && displayText.isBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            // No margin on the bubble's own side: it runs to the edge and the list's contentPadding
            // supplies the air. The opposite side keeps just enough offset to read which side a
            // message came from — the assistant gets nearly the full width, since it is the one
            // sending tables and long lists.
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
                    // SelectionContainer: the user's plain Text was not selectable.
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else if (isAwaitingFirstToken) {
                    TypingIndicator()
                } else {
                    val segments = remember(displayText) {
                        MarkdownTableSegments.split(displayText)
                    }
                    val markdownStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    segments.forEach { segment ->
                        when (segment) {
                            is MarkdownTableSegments.Segment.Text -> MarkdownText(
                                markdown = segment.content,
                                style = markdownStyle,
                                linkColor = MaterialTheme.colorScheme.primary,
                                // SelectionContainer does not reach the library's TextView: its
                                // selection has to be switched on explicitly.
                                isTextSelectable = true
                            )

                            is MarkdownTableSegments.Segment.Table -> AssistantTable(
                                segment = segment,
                                style = markdownStyle
                            )
                        }
                    }
                }
                if (!isAwaitingFirstToken) {
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

        // Actions under the bubble: copy (own prompt and finished answers) and, on assistant
        // answers only, report — required by Google Play's AI policy. Both hide while streaming,
        // since copying or reporting half an answer is no use.
        if (!isStreaming && displayText.isNotBlank()) {
            Row {
                IconButton(
                    onClick = { onCopy(displayText) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.ai_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!isUser && !isReported) {
                    IconButton(
                        onClick = onReport,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = stringResource(R.string.ai_report),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Assistant table at its natural width with horizontal scroll, the way ChatGPT and Gemini do it:
 * swiping beats squeezing four columns into a phone's width and breaking words apart.
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
                modifier = Modifier.width(tableWidth),
                isTextSelectable = true
            )
        }
    }
}

/** Generous per-column width: cells breathe and the overflow scrolls. */
private const val TABLE_COLUMN_WIDTH_DP = 140

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTimestamp(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
}
