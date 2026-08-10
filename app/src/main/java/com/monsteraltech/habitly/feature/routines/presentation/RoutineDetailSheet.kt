package com.monsteraltech.habitly.feature.routines.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineComment
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.feature.routines.presentation.components.CompletionHeatmap
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A routine's progress sheet: streaks, the month's completion, the completions calendar and the
 * holiday-mode switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailSheet(
    detail: RoutineDetailState,
    onDismiss: () -> Unit,
    onMonthShift: (Long) -> Unit,
    onPause: (Long?) -> Unit,
    currentUserId: String = "",
    memberNicknames: Map<String, String> = emptyMap(),
    onCommentDraftChange: (String) -> Unit = {},
    onSendComment: () -> Unit = {},
    onDeleteComment: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RoutineDetailContent(
            detail = detail,
            onMonthShift = onMonthShift,
            onPause = onPause,
            currentUserId = currentUserId,
            memberNicknames = memberNicknames,
            onCommentDraftChange = onCommentDraftChange,
            onSendComment = onSendComment,
            onDeleteComment = onDeleteComment,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)
        )
    }
}

/** Permanent detail surface used beside the routine list on wide screens. */
@Composable
fun RoutineDetailPane(
    detail: RoutineDetailState,
    onDismiss: () -> Unit,
    onMonthShift: (Long) -> Unit,
    onPause: (Long?) -> Unit,
    currentUserId: String = "",
    memberNicknames: Map<String, String> = emptyMap(),
    onCommentDraftChange: (String) -> Unit = {},
    onSendComment: () -> Unit = {},
    onDeleteComment: (String) -> Unit = {}
) {
    RoutineDetailContent(
        detail = detail,
        onMonthShift = onMonthShift,
        onPause = onPause,
        currentUserId = currentUserId,
        memberNicknames = memberNicknames,
        onCommentDraftChange = onCommentDraftChange,
        onSendComment = onSendComment,
        onDeleteComment = onDeleteComment,
        onClose = onDismiss,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    )
}

@Composable
private fun RoutineDetailContent(
    detail: RoutineDetailState,
    onMonthShift: (Long) -> Unit,
    onPause: (Long?) -> Unit,
    currentUserId: String,
    memberNicknames: Map<String, String>,
    onCommentDraftChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onDeleteComment: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val routine = detail.routine
    val today = LocalDate.now()
    val scrollState = rememberScrollState()
    var commentsOffset by remember { mutableIntStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = routine.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routines_detail_close))
                }
            }
        }

        StatsRow(detail = detail, today = today)

        if (routine.streakGraceUsed && routine.currentStreak > 0) {
                Text(
                    text = stringResource(R.string.routines_streak_protected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
        }

        HorizontalDivider()

        MonthSelector(
                month = detail.month,
                canGoForward = detail.month.isBefore(YearMonth.now()),
                onMonthShift = onMonthShift
        )

        if (detail.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
        } else {
                CompletionHeatmap(
                    month = detail.month,
                    completedDates = detail.completedDates,
                    // The calendar (weekly or monthly/yearly anchor), without the pause, window or
                    // interval: it shows the days it falls on by design, not whether it is due today.
                    isDueOn = { date -> RoutineSchedule.isScheduledCalendarDay(routine, date) }
                )
                Text(
                    text = stringResource(R.string.routines_detail_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }

        // Keep the conversation above holiday mode: it is the section people revisit.
        if (routine.type == RoutineType.HOUSEHOLD) {
            HorizontalDivider()
            CommentsSection(
                detail = detail,
                currentUserId = currentUserId,
                memberNicknames = memberNicknames,
                onDraftChange = onCommentDraftChange,
                onSend = onSendComment,
                onDelete = onDeleteComment,
                modifier = Modifier.onGloballyPositioned {
                    commentsOffset = it.positionInParent().y.toInt()
                }
            )
        }

        HorizontalDivider()

        PauseSection(
            pausedUntil = detail.routine.pausedUntil,
            today = today,
            onPause = onPause
        )
    }

    LaunchedEffect(detail.focusComments, commentsOffset) {
        if (detail.focusComments && commentsOffset > 0) {
            scrollState.animateScrollTo(commentsOffset)
        }
    }
}

@Composable
private fun CommentsSection(
    detail: RoutineDetailState,
    currentUserId: String,
    memberNicknames: Map<String, String>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.routines_comments_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (detail.comments.isEmpty()) {
            Text(
                text = stringResource(R.string.routines_comments_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            detail.comments.forEach { comment ->
                CommentRow(
                    comment = comment,
                    isMine = comment.authorId == currentUserId,
                    authorName = if (comment.authorId == currentUserId) {
                        stringResource(R.string.routines_comment_author_you)
                    } else {
                        memberNicknames[comment.authorId]
                            ?: stringResource(R.string.routines_completed_by_unknown)
                    },
                    onDelete = { onDelete(comment.id) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = detail.commentDraft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.routines_comment_hint)) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = MaterialTheme.shapes.medium
            )
            FilledTonalIconButton(
                onClick = onSend,
                enabled = detail.commentDraft.isNotBlank() && !detail.isSendingComment
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.routines_comment_send)
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: RoutineComment,
    isMine: Boolean,
    authorName: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$authorName · ${comment.createdAt.toShortTime()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            }
            // Only your own: the rules let any member write here, but the app should not invite
            // anyone to delete someone else's words.
            if (isMine) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.routines_comment_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Comment timestamp as "5 ago, 18:04" — enough to place it without a full date. */
private fun Long.toShortTime(): String {
    val dateTime = java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val locale = Locale.getDefault()
    val monthName = dateTime.month.getDisplayName(TextStyle.SHORT, locale)
    return "%d %s, %02d:%02d".format(
        dateTime.dayOfMonth, monthName, dateTime.hour, dateTime.minute
    )
}

@Composable
private fun StatsRow(detail: RoutineDetailState, today: LocalDate) {
    val rate = detail.completionRate(today)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = stringResource(R.string.routines_detail_current_streak),
            value = detail.routine.currentStreak.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.routines_detail_best_streak),
            value = detail.routine.bestStreak.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.routines_detail_completion),
            value = rate?.let { "${(it * 100).toInt()}%" } ?: "—",
            modifier = Modifier.weight(1f)
        )
    }

    if (rate == null) {
        Text(
            text = stringResource(R.string.routines_detail_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthSelector(month: YearMonth, canGoForward: Boolean, onMonthShift: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onMonthShift(-1) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.routines_detail_prev_month)
            )
        }
        Text(
            text = month.displayName(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = { onMonthShift(1) }, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.routines_detail_next_month)
            )
        }
    }
}

@Composable
private fun PauseSection(pausedUntil: Long?, today: LocalDate, onPause: (Long?) -> Unit) {
    val pausedUntilDate = pausedUntil?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    val isPaused = pausedUntilDate != null && !today.isAfter(pausedUntilDate)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.routines_pause_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.routines_pause_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isPaused && pausedUntilDate != null) {
            Text(
                text = stringResource(R.string.routines_paused_until, pausedUntilDate.displayName()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(onClick = { onPause(null) }) {
                Text(stringResource(R.string.routines_resume))
            }
        } else {
            var days by remember { mutableIntStateOf(DEFAULT_PAUSE_DAYS) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { if (days > MIN_PAUSE_DAYS) days-- },
                    enabled = days > MIN_PAUSE_DAYS
                ) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = pluralStringResource(R.plurals.routines_interval_days, days, days),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = { if (days < MAX_PAUSE_DAYS) days++ },
                    enabled = days < MAX_PAUSE_DAYS
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }

            FilledTonalButton(
                onClick = {
                    val until = today.plusDays(days.toLong())
                        .atTime(23, 59)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    onPause(until)
                }
            ) {
                Text(pluralStringResource(R.plurals.routines_pause_for_days, days, days))
            }
        }
    }
}

private fun YearMonth.displayName(): String {
    val locale = Locale.getDefault()
    val monthName = month.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$monthName $year"
}

private fun LocalDate.displayName(): String {
    val locale = Locale.getDefault()
    val monthName = month.getDisplayName(TextStyle.FULL, locale)
    return "$dayOfMonth $monthName $year"
}

private const val DEFAULT_PAUSE_DAYS = 7
private const val MIN_PAUSE_DAYS = 1
private const val MAX_PAUSE_DAYS = 60
