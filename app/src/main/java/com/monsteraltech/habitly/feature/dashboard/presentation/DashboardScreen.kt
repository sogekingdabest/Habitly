package com.monsteraltech.habitly.feature.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
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
import java.util.Date
import java.util.Locale

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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Cabecera: fecha + saludo
                item {
                    Column {
                        Text(
                            text = today,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.habitly.accentText
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.dashboard_greeting,
                                uiState.household?.name ?: stringResource(R.string.dashboard_your_home)
                            ),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Tarjeta de compra
                item {
                    ShoppingSummaryCard(
                        pendingCount = uiState.pendingShoppingItems.size,
                        onClick = onNavigateToShopping
                    )
                }

                // Sección "Tus hábitos para hoy"
                item {
                    Text(
                        text = stringResource(R.string.dashboard_habits_today),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
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

                // Añadir rutina
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
private fun ShoppingSummaryCard(
    pendingCount: Int,
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
                Text(
                    text = if (pendingCount == 0) {
                        stringResource(R.string.dashboard_shopping_empty)
                    } else {
                        pluralStringResource(R.plurals.dashboard_pending_products, pendingCount, pendingCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.habitly.textSecondary
                )
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
    HabitlyToggleCard(
        checked = false,
        onCheckedChange = { onToggle() },
        shape = LeafCornerMedium,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RitualToggle(checked = false, size = 34.dp)
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
