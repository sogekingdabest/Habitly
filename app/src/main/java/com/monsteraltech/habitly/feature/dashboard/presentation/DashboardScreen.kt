package com.monsteraltech.habitly.feature.dashboard.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.HabitlyPill
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyToggleCard
import com.monsteraltech.habitly.ui.components.IconHalo
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.components.MineBadge
import com.monsteraltech.habitly.ui.components.RitualToggle
import com.monsteraltech.habitly.ui.components.StreakBadge
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.LeafCornerMedium
import com.monsteraltech.habitly.ui.theme.habitly
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** Productos de la compra que se nombran en la tarjeta resumen antes de resumir en "y N más". */
private const val SHOPPING_PREVIEW_COUNT = 3

@Composable
fun DashboardScreen(
    onNavigateToShopping: () -> Unit = {},
    onNavigateToRoutines: () -> Unit = {},
    onNavigateToAddRoutine: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentlyCompleted by viewModel.recentlyCompleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val dateFormatter = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    val today = dateFormatter.format(Date()).replaceFirstChar { it.uppercase() }

    LaunchedEffect(recentlyCompleted) {
        recentlyCompleted?.let { routine ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.dashboard_routine_completed, routine.title),
                actionLabel = context.getString(R.string.common_undo),
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
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                    DashboardHeader(
                        today = today,
                        householdName = uiState.household?.name,
                        isOffline = uiState.isOffline
                    )
                }

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
                    Text(
                        text = stringResource(R.string.dashboard_habits_today),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (uiState.pendingRoutines.isEmpty()) {
                    item {
                        HabitlyCard(shape = LeafCornerLarge) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.dashboard_routines_done),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.habitly.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.pendingRoutines, key = { it.id }) { routine ->
                        val isMine = routine.type == RoutineType.HOUSEHOLD &&
                            uiState.currentUserId.isNotBlank() &&
                            routine.assignedTo == uiState.currentUserId
                        RoutineDashboardItem(
                            routine = routine,
                            isMine = isMine,
                            onToggle = { viewModel.onToggleRoutine(routine) }
                        )
                    }
                }

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

@Composable
private fun DashboardHeader(today: String, householdName: String?, isOffline: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = today,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.habitly.accentText,
                modifier = Modifier.weight(1f)
            )
            if (isOffline) OfflineBadge()
        }
        Text(
            text = stringResource(
                R.string.dashboard_greeting,
                householdName ?: stringResource(R.string.dashboard_your_home)
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Aviso de que no hay red. Firestore guarda el tick igual y lo sube al volver la conexión;
 * sin este aviso el usuario se cree que ya está sincronizado y se entera tarde.
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
 * Progreso del día: "3 de 7 hechas" con anillo, y debajo el reparto entre miembros.
 *
 * Es lo primero que se mira en un panel familiar y era justo lo que faltaba: antes había que
 * contar las tarjetas a ojo para saber por dónde iba el día.
 */
@Composable
private fun TodayProgressCard(
    done: Int,
    total: Int,
    progress: Float,
    byMember: List<MemberTally>,
    onClick: () -> Unit
) {
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

/** Anillo de progreso, decorativo: el texto de al lado ya dice cuántas van. */
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
                // Arranca arriba y avanza en el sentido del reloj, como se lee un reloj.
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

/**
 * Resumen de la compra. Enseña los primeros productos por su nombre: el recuento a secas
 * obliga a entrar en la pestaña para saber si merece la pena pasar por el súper.
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
                            stringResource(R.string.dashboard_and_more, remaining)
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
private fun RoutineDashboardItem(
    routine: Routine,
    isMine: Boolean,
    onToggle: () -> Unit
) {
    // Estado real, no un literal: con `checked = false` fijo, TalkBack anunciaba "no
    // marcado" aunque la rutina estuviese hecha.
    val isCompleted = RoutineSchedule.isCompletedOn(routine, LocalDate.now())
    val state = stringResource(
        if (isCompleted) R.string.dashboard_routine_state_done
        else R.string.dashboard_routine_state_pending
    )

    HabitlyToggleCard(
        checked = isCompleted,
        onCheckedChange = { onToggle() },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = state },
        shape = LeafCornerMedium,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Solo pinta el estado; quien lo anuncia es la tarjeta, que es el pulsable.
            RitualToggle(checked = isCompleted, size = 34.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (routine.description.isNotBlank()) {
                        Text(
                            text = routine.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.habitly.textSecondary
                        )
                    }
                    if (routine.currentStreak >= 2) {
                        StreakBadge(routine.currentStreak)
                    }
                    if (isMine) {
                        MineBadge(stringResource(R.string.routines_assigned_to_me))
                    }
                }
            }
        }
    }
}
