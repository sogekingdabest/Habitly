package com.monsteraltech.habitly.feature.routines.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = listOf(
        stringResource(R.string.routines_tab_personal),
        stringResource(R.string.routines_tab_household)
    )

    LaunchedEffect(uiState.errorRes) {
        uiState.errorRes?.let { res ->
            snackbarHostState.showSnackbar(context.getString(res))
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.routines_add_routine))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentType = if (selectedTabIndex == 0) RoutineType.PERSONAL else RoutineType.HOUSEHOLD
                val filteredRoutines = uiState.routines.filter { it.type == currentType }.sortedBy { it.order }
                var routinesList by remember(filteredRoutines) { mutableStateOf(filteredRoutines) }

                if (filteredRoutines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.routines_empty_message),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredRoutines, key = { it.id }) { routine ->
                            RoutineCard(
                                routine = routine,
                                isCompleted = RoutinesViewModel.isRoutineCompletedToday(routine),
                                completedByName = routine.lastCompletedBy?.let { uiState.memberNicknames[it] },
                                onToggle = { viewModel.onToggleRoutine(routine) },
                                onEdit = { newTitle, newDescription, newFrequency, newDays, newReminderTime ->
                                    viewModel.onEditRoutine(routine, newTitle, newDescription, newFrequency, newDays, newReminderTime)
                                },
                                onDelete = { viewModel.onDeleteRoutine(routine) },
                                onMoveUp = {
                                    val currentIndex = filteredRoutines.indexOfFirst { it.id == routine.id }
                                    if (currentIndex > 0) {
                                        val newOrder = filteredRoutines.toMutableList()
                                        val item = newOrder.removeAt(currentIndex)
                                        newOrder.add(currentIndex - 1, item)
                                        viewModel.onReorderRoutine(currentType, newOrder.map { it.id })
                                    }
                                },
                                onMoveDown = {
                                    val currentIndex = filteredRoutines.indexOfFirst { it.id == routine.id }
                                    if (currentIndex < filteredRoutines.size - 1) {
                                        val newOrder = filteredRoutines.toMutableList()
                                        val item = newOrder.removeAt(currentIndex)
                                        newOrder.add(currentIndex + 1, item)
                                        viewModel.onReorderRoutine(currentType, newOrder.map { it.id })
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(if (selectedTabIndex == 0) RoutineType.PERSONAL else RoutineType.HOUSEHOLD) }
        var frequency by remember { mutableStateOf(RoutineFrequency.DAILY) }
        var selectedDays by remember { mutableStateOf<List<Int>>(emptyList()) }
        var reminderTime by remember { mutableStateOf<Int?>(null) }
        var showTimePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.routines_new_routine_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.routines_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.routines_field_description)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(stringResource(R.string.routines_type_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = type == RoutineType.PERSONAL,
                            onClick = { type = RoutineType.PERSONAL },
                            label = { Text(stringResource(R.string.routines_type_personal)) }
                        )
                        FilterChip(
                            selected = type == RoutineType.HOUSEHOLD,
                            onClick = { type = RoutineType.HOUSEHOLD },
                            label = { Text(stringResource(R.string.routines_type_household)) }
                        )
                    }

                    Text(stringResource(R.string.routines_frequency_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RoutineFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = {
                                    frequency = freq
                                    if (freq != RoutineFrequency.WEEKLY && freq != RoutineFrequency.CUSTOM) {
                                        selectedDays = emptyList()
                                    }
                                },
                                label = { Text(stringResource(freq.stringRes)) }
                            )
                        }
                    }

                    if (frequency == RoutineFrequency.WEEKLY || frequency == RoutineFrequency.CUSTOM) {
                        Text(stringResource(R.string.routines_select_days_label), style = MaterialTheme.typography.labelLarge)
                        val dayLabels = listOf(
                            Calendar.MONDAY to R.string.routines_day_mon,
                            Calendar.TUESDAY to R.string.routines_day_tue,
                            Calendar.WEDNESDAY to R.string.routines_day_wed,
                            Calendar.THURSDAY to R.string.routines_day_thu,
                            Calendar.FRIDAY to R.string.routines_day_fri,
                            Calendar.SATURDAY to R.string.routines_day_sat,
                            Calendar.SUNDAY to R.string.routines_day_sun
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dayLabels.forEach { (day, res) ->
                                val isSelected = selectedDays.contains(day)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedDays = if (isSelected) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    label = { Text(stringResource(res)) }
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.routines_reminder_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = reminderTime?.let { minutes ->
                                    val hours = minutes / 60
                                    val mins = minutes % 60
                                    stringResource(R.string.routines_reminder_set, "${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}")
                                } ?: stringResource(R.string.routines_reminder_none)
                            )
                        }
                        if (reminderTime != null) {
                            IconButton(onClick = { reminderTime = null }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routines_reminder_clear))
                            }
                        }
                    }

                    if (showTimePicker) {
                        TimePickerDialog(
                            initialMinutes = reminderTime,
                            onDismiss = { showTimePicker = false },
                            onConfirm = { hour, minute ->
                                reminderTime = hour * 60 + minute
                                showTimePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onAddRoutine(title, description, type, frequency, selectedDays, reminderTime)
                        showAddDialog = false
                    },
                    enabled = title.isNotBlank() && (frequency != RoutineFrequency.WEEKLY || selectedDays.isNotEmpty())
                ) {
                    Text(stringResource(R.string.routines_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.routines_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineCard(
    routine: Routine,
    isCompleted: Boolean,
    completedByName: String? = null,
    onToggle: () -> Unit,
    onEdit: (String, String, RoutineFrequency, List<Int>, Int?) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(
                value = isCompleted,
                onValueChange = { onToggle() },
                role = Role.Checkbox
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.routines_move_up),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.routines_move_down),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Indicador visual de completado; el toggle accesible es la Card (Role.Checkbox).
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (routine.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = routine.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isCompleted && routine.type == RoutineType.HOUSEHOLD && routine.lastCompletedBy != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.routines_completed_by,
                            completedByName ?: stringResource(R.string.routines_completed_by_unknown)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (routine.currentStreak >= 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.routines_streak,
                            routine.currentStreak,
                            routine.currentStreak
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IconButton(onClick = { showEditDialog = true }) {
                Icon(
                    Icons.Filled.Edit, 
                    contentDescription = stringResource(R.string.routines_edit_routine),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.routines_delete_routine),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.routines_delete_confirm_title)) },
            text = { Text(stringResource(R.string.routines_delete_confirm_message, routine.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.routines_delete_routine))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.routines_cancel))
                }
            }
        )
    }

    if (showEditDialog) {
        var editTitle by remember { mutableStateOf(routine.title) }
        var editDescription by remember { mutableStateOf(routine.description) }
        var editFrequency by remember { mutableStateOf(routine.frequency) }
        var editSelectedDays by remember { mutableStateOf(routine.scheduledDays) }
        var editReminderTime by remember { mutableStateOf(routine.reminderTime) }
        var showTimePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.routines_edit_routine_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.routines_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text(stringResource(R.string.routines_field_description)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(stringResource(R.string.routines_frequency_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RoutineFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = editFrequency == freq,
                                onClick = {
                                    editFrequency = freq
                                    if (freq != RoutineFrequency.WEEKLY && freq != RoutineFrequency.CUSTOM) {
                                        editSelectedDays = emptyList()
                                    }
                                },
                                label = { Text(stringResource(freq.stringRes)) }
                            )
                        }
                    }

                    if (editFrequency == RoutineFrequency.WEEKLY || editFrequency == RoutineFrequency.CUSTOM) {
                        Text(stringResource(R.string.routines_select_days_label), style = MaterialTheme.typography.labelLarge)
                        val dayLabels = listOf(
                            Calendar.MONDAY to R.string.routines_day_mon,
                            Calendar.TUESDAY to R.string.routines_day_tue,
                            Calendar.WEDNESDAY to R.string.routines_day_wed,
                            Calendar.THURSDAY to R.string.routines_day_thu,
                            Calendar.FRIDAY to R.string.routines_day_fri,
                            Calendar.SATURDAY to R.string.routines_day_sat,
                            Calendar.SUNDAY to R.string.routines_day_sun
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dayLabels.forEach { (day, res) ->
                                val isSelected = editSelectedDays.contains(day)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        editSelectedDays = if (isSelected) {
                                            editSelectedDays - day
                                        } else {
                                            editSelectedDays + day
                                        }
                                    },
                                    label = { Text(stringResource(res)) }
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.routines_reminder_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = editReminderTime?.let { minutes ->
                                    val hours = minutes / 60
                                    val mins = minutes % 60
                                    stringResource(R.string.routines_reminder_set, "${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}")
                                } ?: stringResource(R.string.routines_reminder_none)
                            )
                        }
                        if (editReminderTime != null) {
                            IconButton(onClick = { editReminderTime = null }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routines_reminder_clear))
                            }
                        }
                    }

                    if (showTimePicker) {
                        TimePickerDialog(
                            initialMinutes = editReminderTime,
                            onDismiss = { showTimePicker = false },
                            onConfirm = { hour, minute ->
                                editReminderTime = hour * 60 + minute
                                showTimePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(editTitle, editDescription, editFrequency, editSelectedDays, editReminderTime)
                        showEditDialog = false
                    },
                    enabled = editTitle.isNotBlank() && (editFrequency != RoutineFrequency.WEEKLY || editSelectedDays.isNotEmpty())
                ) {
                    Text(stringResource(R.string.routines_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.routines_cancel))
                }
            }
        )
    }
}

private val RoutineFrequency.stringRes: Int
    get() = when (this) {
        RoutineFrequency.DAILY -> R.string.routines_frequency_daily
        RoutineFrequency.WEEKLY -> R.string.routines_frequency_weekly
        RoutineFrequency.CUSTOM -> R.string.routines_frequency_custom
    }

@Composable
fun TimePickerDialog(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialMinutes?.let { it / 60 } ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialMinutes?.let { it % 60 } ?: Calendar.getInstance().get(Calendar.MINUTE)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_reminder_label)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (hour > 0) hour-- }) {
                        Text("-", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        text = hour.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = minute.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(onClick = { if (hour < 23) hour++ }) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (minute > 0) minute -= 5 }) {
                        Text("-", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { if (minute < 55) minute += 5 }) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) {
                Text(stringResource(R.string.routines_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routines_cancel))
            }
        }
    )
}
