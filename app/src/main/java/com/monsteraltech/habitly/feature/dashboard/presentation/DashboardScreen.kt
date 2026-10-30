package com.monsteraltech.habitly.feature.dashboard.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.HabitlyEmptyState
import com.monsteraltech.habitly.ui.components.HabitlyListSection
import com.monsteraltech.habitly.ui.components.HabitlyListSkeleton
import com.monsteraltech.habitly.ui.components.HabitlyPill
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyRowDivider
import com.monsteraltech.habitly.ui.components.HabitlyRowMeta
import com.monsteraltech.habitly.ui.components.HabitlySkeleton
import com.monsteraltech.habitly.ui.components.HabitlyToggleRow
import com.monsteraltech.habitly.ui.components.HabitlyTopBar
import com.monsteraltech.habitly.ui.components.IconHalo
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.components.MineBadge
import com.monsteraltech.habitly.ui.components.RitualToggle
import com.monsteraltech.habitly.ui.components.StreakBadge
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** Products named on the summary card before collapsing into "and N more". */
private const val SHOPPING_PREVIEW_COUNT = 3

/** A streak only earns a badge once it is worth bragging about. */
private const val MIN_STREAK_SHOWN = 2

@Composable
fun DashboardScreen(
    onNavigateToShopping: () -> Unit = {},
    onNavigateToRoutines: () -> Unit = {},
    onNavigateToAddRoutine: () -> Unit = {},
    onNavigateToNotes: (NoteType) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentlyCompleted by viewModel.recentlyCompleted.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val recentlyCompletedMessage = recentlyCompleted?.let { routine ->
        stringResource(R.string.dashboard_routine_completed, routine.title)
    }
    val undoLabel = stringResource(R.string.common_undo)

    val dateFormatter = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    val today = dateFormatter.format(Date()).replaceFirstChar { it.uppercase() }

    LaunchedEffect(recentlyCompleted, recentlyCompletedMessage, undoLabel) {
        recentlyCompleted?.let { routine ->
            val result = snackbarHostState.showSnackbar(
                message = checkNotNull(recentlyCompletedMessage),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoComplete()
            } else {
                viewModel.onUndoShown()
            }
        }
    }

    HabitlyBackground(arrangement = MeshArrangement.Home) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                HabitlyTopBar(
                    overline = today,
                    title = stringResource(
                        R.string.dashboard_greeting,
                        uiState.household?.name ?: stringResource(R.string.dashboard_your_home)
                    ),
                    actions = { if (uiState.isOffline) OfflineBadge() }
                )
            }
        ) { innerPadding ->
            if (uiState.isLoading) {
                // A skeleton with the shape of the list, not a centred spinner: the spinner made
                // the whole page jump into place the moment the data arrived.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HabitlySkeleton(width = 180.dp, height = 22.dp)
                    HabitlyListSkeleton(loadingLabel = stringResource(R.string.cd_loading))
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TodayProgressCard(
                        done = uiState.todayRoutinesDone,
                        total = uiState.todayRoutinesTotal,
                        progress = uiState.todayProgress,
                        byMember = uiState.todayByMember,
                        onClick = onNavigateToRoutines
                    )
                }

                item {
                    ShoppingSummaryCard(
                        pendingNames = uiState.pendingShoppingItems.map { it.name },
                        onClick = onNavigateToShopping
                    )
                }

                item {
                    NotesSummaryCard(
                        count = uiState.notesCount,
                        latestHeading = uiState.latestNoteHeading,
                        latestType = uiState.latestNoteType,
                        onClick = { onNavigateToNotes(uiState.latestNoteType ?: NoteType.PERSONAL) }
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.dashboard_habits_today),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // One card for the whole list instead of one per routine: the same rows go from
                // ~92dp to 56dp, so the day fits on screen instead of being scrolled through.
                item {
                    HabitlyListSection {
                        if (uiState.pendingRoutines.isEmpty()) {
                            HabitlyEmptyState(
                                icon = Icons.Outlined.TaskAlt,
                                title = stringResource(R.string.dashboard_routines_done)
                            )
                        } else {
                            uiState.pendingRoutines.forEachIndexed { index, routine ->
                                if (index > 0) HabitlyRowDivider()
                                val isMine = routine.type == RoutineType.HOUSEHOLD &&
                                    uiState.currentUserId.isNotBlank() &&
                                    routine.assignedTo == uiState.currentUserId
                                RoutineDashboardRow(
                                    routine = routine,
                                    isMine = isMine,
                                    onToggle = { viewModel.onToggleRoutine(routine) }
                                )
                            }
                        }
                    }
                }

                // Add routine
                item {
                    HabitlyPrimaryButton(
                        text = stringResource(R.string.routines_add_routine),
                        onClick = onNavigateToAddRoutine,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Offline warning. Firestore stores the tick anyway and uploads it on reconnect; without this the
 * user assumes everything is already synced and finds out too late.
 */
@Composable
private fun OfflineBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        HabitlyPill(
            text = stringResource(R.string.dashboard_offline),
            background = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * Progress for the day: "3 of 7 done" with a ring, and the split between members below. It is the
 * first thing anyone looks at on a family dashboard.
 */
@Composable
private fun TodayProgressCard(
    done: Int,
    total: Int,
    progress: Float,
    byMember: List<MemberTally>,
    onClick: () -> Unit
) {
    // Clickable, like the shopping card: the summary jumps to the full routines list, which the
    // dashboard deliberately no longer shows in full.
    HabitlyCard(shape = LeafCornerLarge, contentPadding = PaddingValues(16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = progress)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (total == 0) {
                        stringResource(R.string.dashboard_today_nothing)
                    } else {
                        pluralStringResource(R.plurals.dashboard_today_progress, done, done, total)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (byMember.isNotEmpty()) {
                    val unknown = stringResource(R.string.routines_completed_by_unknown)
                    Text(
                        text = stringResource(
                            R.string.dashboard_today_by_member,
                            byMember.joinToString(" · ") { "${it.name.ifBlank { unknown }} ${it.count}" }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.habitly.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/** Progress ring, decorative: the text next to it already states the count. */
@Composable
private fun ProgressRing(progress: Float) {
    val track = MaterialTheme.habitly.border
    val fill = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(44.dp).clearAndSetSemantics { }) {
        val stroke = size.minDimension * 0.16f
        val inset = stroke / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - stroke,
            size.height - stroke
        )
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = fill,
                // Starts at the top and sweeps clockwise, the way a clock is read.
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** Notes at a glance: how many there are and what the most recent one says. */
@Composable
private fun NotesSummaryCard(
    count: Int,
    latestHeading: String,
    latestType: NoteType?,
    onClick: () -> Unit
) {
    HabitlyCard(shape = LeafCornerLarge, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconHalo {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notes_dashboard_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (count == 0) {
                    Text(
                        text = stringResource(R.string.notes_dashboard_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.habitly.textSecondary
                    )
                } else {
                    Text(
                        text = latestHeading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(
                            if (latestType == NoteType.HOUSEHOLD) R.string.notes_scope_household
                            else R.string.notes_scope_personal
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = pluralStringResource(R.plurals.notes_dashboard_count, count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.habitly.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Shopping summary. Names the first few products: a bare count forces the user into the tab just
 * to decide whether a supermarket trip is worth it.
 */
@Composable
private fun ShoppingSummaryCard(
    pendingNames: List<String>,
    onClick: () -> Unit
) {
    HabitlyCard(shape = LeafCornerLarge, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconHalo {
                Icon(
                    Icons.Outlined.ShoppingCart,
                    // Decorative: the title next to it already names the card.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_shopping_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (pendingNames.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_shopping_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.habitly.textSecondary
                    )
                } else {
                    Text(
                        text = pendingNames.take(SHOPPING_PREVIEW_COUNT).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val remaining = pendingNames.size - SHOPPING_PREVIEW_COUNT
                    Text(
                        text = if (remaining > 0) {
                            pluralStringResource(R.plurals.dashboard_and_more, remaining, remaining)
                        } else {
                            pluralStringResource(
                                R.plurals.dashboard_pending_products,
                                pendingNames.size,
                                pendingNames.size
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.habitly.textSecondary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            HabitlyPill(
                text = stringResource(R.string.dashboard_see_more),
                background = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.habitly.accentText
            )
        }
    }
}

@Composable
private fun RoutineDashboardRow(
    routine: Routine,
    isMine: Boolean,
    onToggle: () -> Unit
) {
    // Real state, not a literal: with a hardcoded `checked = false`, TalkBack announced
    // "not checked" even for routines that were done.
    val isCompleted = RoutineSchedule.isCompletedOn(routine, LocalDate.now())
    val state = stringResource(
        if (isCompleted) R.string.dashboard_routine_state_done
        else R.string.dashboard_routine_state_pending
    )

    HabitlyToggleRow(
        checked = isCompleted,
        onCheckedChange = { onToggle() },
        stateLabel = state,
        // Only draws the state; the row announces it, since the row is the toggle.
        leading = { RitualToggle(checked = isCompleted, size = 28.dp) }
    ) {
        Text(
            text = routine.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val hasMeta = routine.description.isNotBlank() ||
            routine.currentStreak >= MIN_STREAK_SHOWN ||
            isMine
        if (hasMeta) {
            HabitlyRowMeta {
                if (routine.description.isNotBlank()) {
                    Text(
                        text = routine.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.habitly.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (routine.currentStreak >= MIN_STREAK_SHOWN) {
                    StreakBadge(routine.currentStreak)
                }
                if (isMine) {
                    MineBadge(stringResource(R.string.routines_assigned_to_me))
                }
            }
        }
    }
}
