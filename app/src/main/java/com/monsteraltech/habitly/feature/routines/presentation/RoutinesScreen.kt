package com.monsteraltech.habitly.feature.routines.presentation

import android.content.res.Configuration
import android.text.format.DateFormat
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.feature.routines.presentation.components.AnchorHintText
import com.monsteraltech.habitly.feature.routines.presentation.components.DateWindowFields
import com.monsteraltech.habitly.feature.routines.presentation.components.HouseholdBalanceCard
import com.monsteraltech.habitly.feature.routines.presentation.components.IconPickerField
import com.monsteraltech.habitly.feature.routines.presentation.components.IntervalSelector
import com.monsteraltech.habitly.feature.routines.presentation.components.NotificationLevelSelector
import com.monsteraltech.habitly.feature.routines.presentation.components.formatRoutineDate
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlySwipeRow
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.components.RitualToggle
import com.monsteraltech.habitly.ui.components.swipeRowSemantics
import com.monsteraltech.habitly.ui.theme.LeafCornerMedium
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.util.Calendar

/** What the routine create/edit form returns. */
data class RoutineFormResult(
    val title: String,
    val description: String,
    val type: RoutineType,
    val frequency: RoutineFrequency,
    val scheduledDays: List<Int>,
    val reminderTime: Int?,
    val intervalDays: Int?,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val icon: String = "",
    val notificationLevel: NotificationLevel = NotificationLevel.DEFAULT,
    val rotationEnabled: Boolean = false,
    val assignedTo: String? = null
)

private enum class RoutineListMode { TODAY, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onNavigateToAddRoutine: (RoutineType) -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTypeIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val today = LocalDate.now()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val selectedRoutineType = if (selectedTypeIndex == 0) {
        RoutineType.PERSONAL
    } else {
        RoutineType.HOUSEHOLD
    }

    val typeTabs = listOf(
        stringResource(R.string.routines_tab_personal),
        stringResource(R.string.routines_tab_household)
    )
    val modeTabs = listOf(
        stringResource(R.string.routines_filter_today),
        stringResource(R.string.routines_filter_all)
    )
    val errorMessage = uiState.errorRes?.let { stringResource(it) }

    LaunchedEffect(uiState.errorRes, errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorShown()
        }
    }

    HabitlyBackground(arrangement = MeshArrangement.Routines) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isLandscape) {
                FloatingActionButton(
                    onClick = { onNavigateToAddRoutine(selectedRoutineType) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.routines_add_routine))
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        val isWide = maxWidth >= 1000.dp
        val listPanel: @Composable (Modifier) -> Unit = { panelModifier ->
        Column(
            modifier = panelModifier
        ) {
            PrimaryTabRow(selectedTabIndex = selectedModeIndex) {
                modeTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedModeIndex == index,
                        onClick = {
                            selectedModeIndex = index
                            viewModel.onCloseRoutineDetail()
                        },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    typeTabs.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = selectedTypeIndex == index,
                            onClick = {
                                selectedTypeIndex = index
                                viewModel.onCloseRoutineDetail()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = typeTabs.size)
                        ) {
                            Text(title)
                        }
                    }
                }
                if (isLandscape) {
                    FilledTonalIconButton(
                        onClick = { onNavigateToAddRoutine(selectedRoutineType) }
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.routines_add_routine)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentType = selectedRoutineType
                val currentMode = if (selectedModeIndex == 0) RoutineListMode.TODAY else RoutineListMode.ALL
                val routinesOfType = uiState.routines
                    .filter { it.type == currentType }
                    .sortedBy { it.order }
                val filteredRoutines = routinesOfType.filter { routine ->
                    currentMode == RoutineListMode.ALL ||
                        RoutineSchedule.isPendingOn(routine, today) ||
                        RoutineSchedule.isCompletedOn(routine, today)
                }
                val routineGroups: List<Pair<Int?, List<Routine>>> = if (currentMode == RoutineListMode.TODAY) {
                    listOf(null to filteredRoutines)
                } else {
                    val finished = routinesOfType.filter { RoutineSchedule.isFinishedOn(it, today) }
                    val upcoming = routinesOfType.filter {
                        it !in finished && RoutineSchedule.isNotStartedOn(it, today)
                    }
                    val paused = routinesOfType.filter {
                        it !in finished && it !in upcoming && RoutineSchedule.isPausedOn(it, today)
                    }
                    val active = routinesOfType.filter {
                        it !in finished && it !in upcoming && it !in paused
                    }
                    listOf(
                        R.string.routines_group_active to active,
                        R.string.routines_group_paused to paused,
                        R.string.routines_group_upcoming to upcoming,
                        R.string.routines_group_finished to finished
                    )
                }

                if (routineGroups.all { it.second.isEmpty() }) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(
                                if (currentMode == RoutineListMode.TODAY) R.string.routines_empty_today
                                else R.string.routines_empty_message
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        routineGroups.forEach { (titleRes, groupedRoutines) ->
                            if (titleRes != null && groupedRoutines.isNotEmpty()) {
                                item(key = "group_$titleRes") {
                                    Text(
                                        text = stringResource(titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        items(groupedRoutines, key = { it.id }) { routine ->
                            val currentIndex = routinesOfType.indexOfFirst { it.id == routine.id }
                            RoutineCard(
                                routine = routine,
                                isCompleted = RoutineSchedule.isCompletedOn(routine, today),
                                isPaused = RoutineSchedule.isPausedOn(routine, today),
                                completedByName = routine.lastCompletedBy?.let { uiState.memberNicknames[it] },
                                assignedToName = routine.assignedTo?.let { uiState.memberNicknames[it] },
                                isAssignedToMe = routine.assignedTo == uiState.currentUserId,
                                members = uiState.householdMembers,
                                memberNicknames = uiState.memberNicknames,
                                hasNewComments = routine.id in uiState.routinesWithNewComments,
                                onToggle = { viewModel.onToggleRoutine(routine) },
                                onEdit = { form ->
                                    viewModel.onEditRoutine(
                                        routine, form.title, form.description, form.frequency,
                                        form.scheduledDays, form.reminderTime, form.intervalDays,
                                        routine.pausedUntil, form.startDate, form.endDate,
                                        form.icon, form.notificationLevel,
                                        form.rotationEnabled, form.assignedTo
                                    )
                                },
                                onDelete = { viewModel.onDeleteRoutine(routine) },
                                onOpenDetail = { viewModel.onOpenRoutineDetail(routine) },
                                onOpenComments = {
                                    viewModel.onOpenRoutineDetail(routine, focusComments = true)
                                },
                                onMoveUp = if (currentMode == RoutineListMode.ALL && currentIndex > 0) {
                                    {
                                        val newOrder = routinesOfType.toMutableList()
                                        val item = newOrder.removeAt(currentIndex)
                                        newOrder.add(currentIndex - 1, item)
                                        viewModel.onReorderRoutine(currentType, newOrder.map { it.id })
                                    }
                                } else null,
                                onMoveDown = if (
                                    currentMode == RoutineListMode.ALL && currentIndex < routinesOfType.lastIndex
                                ) {
                                    {
                                        val newOrder = routinesOfType.toMutableList()
                                        val item = newOrder.removeAt(currentIndex)
                                        newOrder.add(currentIndex + 1, item)
                                        viewModel.onReorderRoutine(currentType, newOrder.map { it.id })
                                    }
                                } else null
                            )
                        }
                        }

                        if (currentType == RoutineType.HOUSEHOLD && currentMode == RoutineListMode.ALL) {
                            item(key = "balance") {
                                HouseholdBalanceCard(
                                    balance = uiState.weeklyBalance,
                                    memberNicknames = uiState.memberNicknames,
                                    members = uiState.householdMembers,
                                    currentUserId = uiState.currentUserId
                                )
                            }
                        }
                    }
            }
        }
        }
        }

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 1400.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                listPanel(Modifier.weight(0.5f).fillMaxHeight())
                VerticalDivider()
                Box(
                    modifier = Modifier.weight(0.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val detail = uiState.routineDetail
                    if (detail == null) {
                        Text(
                            text = stringResource(R.string.routines_select_detail),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        RoutineDetailPane(
                            detail = detail,
                            onDismiss = viewModel::onCloseRoutineDetail,
                            onMonthShift = viewModel::onDetailMonthShift,
                            onPause = { viewModel.onSetPaused(detail.routine, it) },
                            currentUserId = uiState.currentUserId,
                            memberNicknames = uiState.memberNicknames,
                            onCommentDraftChange = viewModel::onCommentDraftChange,
                            onSendComment = viewModel::onSendComment,
                            onDeleteComment = viewModel::onDeleteComment
                        )
                    }
                }
            }
        } else {
            listPanel(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
            uiState.routineDetail?.let { detail ->
                RoutineDetailSheet(
                    detail = detail,
                    onDismiss = viewModel::onCloseRoutineDetail,
                    onMonthShift = viewModel::onDetailMonthShift,
                    onPause = { viewModel.onSetPaused(detail.routine, it) },
                    currentUserId = uiState.currentUserId,
                    memberNicknames = uiState.memberNicknames,
                    onCommentDraftChange = viewModel::onCommentDraftChange,
                    onSendComment = viewModel::onSendComment,
                    onDeleteComment = viewModel::onDeleteComment
                )
            }
        }
        }
    }
    }
}

/**
 * Form shared by create and edit: these used to be two near-identical dialogs, and each new field
 * (like the interval) had to be added in both.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RoutineFormDialog(
    dialogTitleRes: Int,
    confirmLabelRes: Int,
    onDismiss: () -> Unit,
    onConfirm: (RoutineFormResult) -> Unit,
    showTypeSelector: Boolean = false,
    initialTitle: String = "",
    initialDescription: String = "",
    initialType: RoutineType = RoutineType.PERSONAL,
    initialFrequency: RoutineFrequency = RoutineFrequency.DAILY,
    initialDays: List<Int> = emptyList(),
    initialReminderTime: Int? = null,
    initialIntervalDays: Int? = null,
    initialStartDate: Long? = null,
    initialEndDate: Long? = null,
    initialIcon: String = "",
    initialNotificationLevel: NotificationLevel = NotificationLevel.DEFAULT,
    initialRotationEnabled: Boolean = false,
    initialAssignedTo: String? = null,
    members: List<String> = emptyList(),
    memberNicknames: Map<String, String> = emptyMap()
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var type by remember { mutableStateOf(initialType) }
    var frequency by remember { mutableStateOf(initialFrequency) }
    var selectedDays by remember { mutableStateOf(initialDays) }
    var reminderTime by remember { mutableStateOf(initialReminderTime) }
    var intervalDays by remember { mutableIntStateOf(initialIntervalDays ?: DEFAULT_INTERVAL_DAYS) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }
    var icon by remember { mutableStateOf(initialIcon) }
    var notificationLevel by remember { mutableStateOf(initialNotificationLevel) }
    var rotationEnabled by remember { mutableStateOf(initialRotationEnabled) }
    var assignedTo by remember { mutableStateOf(initialAssignedTo) }
    var showTimePicker by remember { mutableStateOf(false) }

    val needsDays = frequency == RoutineFrequency.WEEKLY || frequency == RoutineFrequency.CUSTOM
    val endBeforeStart = startDate != null && endDate != null && endDate!! < startDate!!
    // Rotation only makes sense in a household with more than one member.
    val canRotate = type == RoutineType.HOUSEHOLD && members.size > 1

    val canSave = title.isNotBlank() &&
        (frequency != RoutineFrequency.WEEKLY || selectedDays.isNotEmpty()) &&
        !endBeforeStart

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(dialogTitleRes)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.routines_cancel)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 8.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.routines_cancel))
                            }
                            Button(
                                onClick = {
                                    onConfirm(
                                        RoutineFormResult(
                                            title = title,
                                            description = description,
                                            type = type,
                                            frequency = frequency,
                                            scheduledDays = selectedDays,
                                            reminderTime = reminderTime,
                                            intervalDays = if (frequency == RoutineFrequency.EVERY_N_DAYS) intervalDays else null,
                                            startDate = startDate,
                                            endDate = endDate,
                                            icon = icon,
                                            notificationLevel = notificationLevel,
                                            rotationEnabled = canRotate && rotationEnabled,
                                            assignedTo = if (canRotate && rotationEnabled) assignedTo else null
                                        )
                                    )
                                },
                                enabled = canSave
                            ) {
                                Text(stringResource(confirmLabelRes))
                            }
                        }
                    }
                }
            ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
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

                Text(stringResource(R.string.routines_icon_label), style = MaterialTheme.typography.labelLarge)
                IconPickerField(icon = icon, onIconChange = { icon = it })

                if (showTypeSelector) {
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
                }

                Text(stringResource(R.string.routines_frequency_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RoutineFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = {
                                frequency = freq
                                if (freq != RoutineFrequency.WEEKLY && freq != RoutineFrequency.CUSTOM) {
                                    selectedDays = emptyList()
                                }
                                // Monthly/yearly need an anchor date; default it to today.
                                if ((freq == RoutineFrequency.MONTHLY || freq == RoutineFrequency.YEARLY) &&
                                    startDate == null
                                ) {
                                    startDate = LocalDate.now()
                                        .atStartOfDay(java.time.ZoneId.systemDefault())
                                        .toInstant().toEpochMilli()
                                }
                            },
                            label = { Text(stringResource(freq.stringRes)) }
                        )
                    }
                }

                if (needsDays) {
                    Text(stringResource(R.string.routines_select_days_label), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DAY_LABELS.forEach { (day, res) ->
                            val isSelected = selectedDays.contains(day)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                },
                                label = { Text(stringResource(res)) }
                            )
                        }
                    }
                }

                if (frequency == RoutineFrequency.EVERY_N_DAYS) {
                    Text(stringResource(R.string.routines_interval_label), style = MaterialTheme.typography.labelLarge)
                    IntervalSelector(
                        intervalDays = intervalDays,
                        onIntervalChange = { intervalDays = it }
                    )
                }

                if (frequency == RoutineFrequency.MONTHLY || frequency == RoutineFrequency.YEARLY) {
                    AnchorHintText(
                        frequency = frequency,
                        anchorMillis = startDate ?: System.currentTimeMillis()
                    )
                }

                // Lifetime window.
                Text(stringResource(R.string.routines_dates_section), style = MaterialTheme.typography.labelLarge)
                DateWindowFields(
                    startDate = startDate,
                    endDate = endDate,
                    onStartChange = { startDate = it },
                    onEndChange = { endDate = it }
                )

                if (canRotate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.routines_rotation_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                stringResource(R.string.routines_rotation_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rotationEnabled,
                            onCheckedChange = { enabled ->
                                rotationEnabled = enabled
                                // Turning it on has to start with someone.
                                if (enabled && assignedTo == null) assignedTo = members.firstOrNull()
                                if (!enabled) assignedTo = null
                            }
                        )
                    }

                    if (rotationEnabled) {
                        Text(
                            stringResource(R.string.routines_rotation_starts_with),
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            members.forEach { memberId ->
                                FilterChip(
                                    selected = assignedTo == memberId,
                                    onClick = { assignedTo = memberId },
                                    label = {
                                        Text(
                                            memberNicknames[memberId]
                                                ?: stringResource(R.string.routines_completed_by_unknown)
                                        )
                                    }
                                )
                            }
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
                                stringResource(
                                    R.string.routines_reminder_set,
                                    "${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}"
                                )
                            } ?: stringResource(R.string.routines_reminder_none)
                        )
                    }
                    if (reminderTime != null) {
                        IconButton(onClick = { reminderTime = null }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routines_reminder_clear))
                        }
                    }
                }

                if (reminderTime != null) {
                    Text(
                        stringResource(R.string.routines_level_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    NotificationLevelSelector(
                        level = notificationLevel,
                        onLevelChange = { notificationLevel = it }
                    )
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
            }
        }
    }
}

@Composable
fun RoutineCard(
    routine: Routine,
    isCompleted: Boolean,
    onToggle: () -> Unit,
    onEdit: (RoutineFormResult) -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    isPaused: Boolean = false,
    completedByName: String? = null,
    assignedToName: String? = null,
    isAssignedToMe: Boolean = false,
    members: List<String> = emptyList(),
    memberNicknames: Map<String, String> = emptyMap(),
    hasNewComments: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val newCommentsLabel = stringResource(R.string.routines_comments_new)

    val toggleLabel = stringResource(
        if (isCompleted) R.string.routines_a11y_uncomplete else R.string.routines_a11y_complete
    )
    // Completing is deliberately the only swipe gesture. Destructive and administrative actions
    // live in the overflow menu, where they are discoverable without crowding the row.
    HabitlySwipeRow(
        onPrimaryAction = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
    HabitlyCard(
        modifier = Modifier
            .fillMaxWidth()
            .swipeRowSemantics(
                primaryLabel = toggleLabel,
                onPrimaryAction = onToggle
            ),
        shape = LeafCornerMedium,
        contentPadding = PaddingValues(0.dp),
        onClick = onOpenDetail
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .toggleable(
                        value = isCompleted,
                        onValueChange = { onToggle() },
                        role = Role.Checkbox
                    ),
                contentAlignment = Alignment.Center
            ) {
                RitualToggle(checked = isCompleted, size = 28.dp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // The emoji rides in the same Text as the title so it wraps and strikes through
                    // with it, instead of floating in its own column.
                    text = if (routine.icon.isBlank()) routine.title else "${routine.icon} ${routine.title}",
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

                if (isPaused) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.routines_pause_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Whose turn it is. "It's your turn" stands out because it is the actionable part.
                if (routine.assignedTo != null && !isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAssignedToMe) {
                            stringResource(R.string.routines_assigned_to_me)
                        } else {
                            stringResource(
                                R.string.routines_assigned_to,
                                assignedToName ?: stringResource(R.string.routines_completed_by_unknown)
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAssignedToMe) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isAssignedToMe) FontWeight.Bold else FontWeight.SemiBold
                    )
                }

                if (routine.currentStreak >= MIN_STREAK_SHOWN) {
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
                    if (routine.streakGraceUsed) {
                        Text(
                            text = stringResource(R.string.routines_streak_protected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Every shared routine shows how to enter the conversation, including an empty one.
                if (routine.type == RoutineType.HOUSEHOLD) {
                    val commentsTint = if (hasNewComments) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onOpenComments)
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.ChatBubbleOutline,
                            contentDescription = null,
                            tint = commentsTint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (routine.commentCount > 0) {
                                pluralStringResource(
                                    R.plurals.routines_comments_count,
                                    routine.commentCount,
                                    routine.commentCount
                                )
                            } else {
                                stringResource(R.string.routines_comments_add)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = commentsTint,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (hasNewComments) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .semantics {
                                        contentDescription = newCommentsLabel
                                    }
                            )
                        }
                    }
                }

                // Lifetime window badge: finished (end passed) or not started yet.
                val cardToday = LocalDate.now()
                when {
                    RoutineSchedule.isFinishedOn(routine, cardToday) -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.routines_finished_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    RoutineSchedule.isNotStartedOn(routine, cardToday) && routine.startDate != null -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.routines_starts_on,
                                formatRoutineDate(routine.startDate)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.routines_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_edit_routine)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            showEditDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_move_up)) },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                        enabled = onMoveUp != null,
                        onClick = {
                            menuExpanded = false
                            onMoveUp?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_move_down)) },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                        enabled = onMoveDown != null,
                        onClick = {
                            menuExpanded = false
                            onMoveDown?.invoke()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.routines_delete_routine),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
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
        RoutineFormDialog(
            dialogTitleRes = R.string.routines_edit_routine_title,
            confirmLabelRes = R.string.routines_save,
            showTypeSelector = false,
            initialTitle = routine.title,
            initialDescription = routine.description,
            initialType = routine.type,
            initialFrequency = routine.frequency,
            initialDays = routine.scheduledDays,
            initialReminderTime = routine.reminderTime,
            initialIntervalDays = routine.intervalDays,
            initialStartDate = routine.startDate,
            initialEndDate = routine.endDate,
            initialIcon = routine.icon,
            initialNotificationLevel = routine.notificationLevel,
            initialRotationEnabled = routine.rotationEnabled,
            initialAssignedTo = routine.assignedTo,
            members = members,
            memberNicknames = memberNicknames,
            onDismiss = { showEditDialog = false },
            onConfirm = { form ->
                onEdit(form)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes?.let { it / 60 }
            ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = initialMinutes?.let { it % 60 }
            ?: Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_reminder_pick_time)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
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

private val RoutineFrequency.stringRes: Int
    get() = when (this) {
        RoutineFrequency.DAILY -> R.string.routines_frequency_daily
        RoutineFrequency.WEEKLY -> R.string.routines_frequency_weekly
        RoutineFrequency.CUSTOM -> R.string.routines_frequency_custom
        RoutineFrequency.EVERY_N_DAYS -> R.string.routines_frequency_interval
        RoutineFrequency.MONTHLY -> R.string.routines_frequency_monthly
        RoutineFrequency.YEARLY -> R.string.routines_frequency_yearly
    }

private val DAY_LABELS = listOf(
    Calendar.MONDAY to R.string.routines_day_mon,
    Calendar.TUESDAY to R.string.routines_day_tue,
    Calendar.WEDNESDAY to R.string.routines_day_wed,
    Calendar.THURSDAY to R.string.routines_day_thu,
    Calendar.FRIDAY to R.string.routines_day_fri,
    Calendar.SATURDAY to R.string.routines_day_sat,
    Calendar.SUNDAY to R.string.routines_day_sun
)

private const val MIN_STREAK_SHOWN = 2
private const val DEFAULT_INTERVAL_DAYS = 3
private const val MIN_INTERVAL_DAYS = 1
private const val MAX_INTERVAL_DAYS = 365
