package com.monsteraltech.habitly.feature.routines.presentation.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.presentation.TimePickerDialog
import com.monsteraltech.habitly.feature.routines.presentation.components.AnchorHintText
import com.monsteraltech.habitly.feature.routines.presentation.components.DateWindowFields
import com.monsteraltech.habitly.feature.routines.presentation.components.IconPickerField
import com.monsteraltech.habitly.feature.routines.presentation.components.IntervalSelector
import com.monsteraltech.habitly.feature.routines.presentation.components.NotificationLevelSelector
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/**
 * Full-screen routine creation: replaces the old floating dialog, which ran out of room the moment
 * a routine carried days, a reminder or turns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoutineScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddRoutineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success) {
        if (uiState.success) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines_new_routine_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            // ---------- What routine it is ----------
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(R.string.routines_add_section_what))
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text(stringResource(R.string.routines_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text(stringResource(R.string.routines_field_description)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                IconPickerField(
                    icon = uiState.icon,
                    onIconChange = viewModel::onIconChange
                )
            }

            // ---------- Whose it is ----------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.routines_add_section_type))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.type == RoutineType.PERSONAL,
                        onClick = { viewModel.onTypeChange(RoutineType.PERSONAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.routines_type_personal))
                    }
                    SegmentedButton(
                        selected = uiState.type == RoutineType.HOUSEHOLD,
                        onClick = { viewModel.onTypeChange(RoutineType.HOUSEHOLD) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.routines_type_household))
                    }
                }
                Text(
                    text = stringResource(
                        if (uiState.type == RoutineType.PERSONAL) R.string.routines_type_personal_desc
                        else R.string.routines_type_household_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- How often it is due ----------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.routines_add_section_frequency))
                RoutineFrequency.entries.forEach { freq ->
                    FrequencyOption(
                        selected = uiState.frequency == freq,
                        title = stringResource(freq.titleRes),
                        description = stringResource(freq.descRes),
                        onClick = { viewModel.onFrequencyChange(freq) }
                    )
                }

                AnimatedVisibility(visible = uiState.needsDays) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.routines_select_days_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DAY_LABELS.forEach { (day, labelRes) ->
                                DayCircle(
                                    label = stringResource(labelRes),
                                    dayName = day.toDayOfWeekName(),
                                    selected = uiState.selectedDays.contains(day),
                                    onToggle = { viewModel.onDayToggle(day) }
                                )
                            }
                        }
                        if (uiState.frequency == RoutineFrequency.WEEKLY && uiState.selectedDays.isEmpty()) {
                            Text(
                                text = stringResource(R.string.routines_days_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.frequency == RoutineFrequency.EVERY_N_DAYS) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.routines_interval_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        IntervalSelector(
                            intervalDays = uiState.intervalDays,
                            onIntervalChange = viewModel::onIntervalSet
                        )
                    }
                }

                AnimatedVisibility(
                    visible = uiState.frequency == RoutineFrequency.MONTHLY ||
                        uiState.frequency == RoutineFrequency.YEARLY
                ) {
                    AnchorHintText(
                        frequency = uiState.frequency,
                        anchorMillis = uiState.startDate ?: System.currentTimeMillis(),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ---------- Duration (lifetime window) ----------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.routines_dates_section))
                DateWindowFields(
                    startDate = uiState.startDate,
                    endDate = uiState.endDate,
                    onStartChange = viewModel::onStartDateChange,
                    onEndChange = viewModel::onEndDateChange
                )
            }

            // ---------- Reminder ----------
            SettingCard(
                icon = { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.routines_reminder_title),
                description = stringResource(R.string.routines_reminder_switch_desc),
                checked = uiState.reminderEnabled,
                onCheckedChange = viewModel::onReminderToggle
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { showTimePicker = true }) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "%02d:%02d".format(uiState.reminderMinutes / 60, uiState.reminderMinutes % 60),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.routines_level_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    NotificationLevelSelector(
                        level = uiState.notificationLevel,
                        onLevelChange = viewModel::onNotificationLevelChange
                    )
                }
            }

            // ---------- Rotating turns ----------
            if (uiState.canRotate) {
                SettingCard(
                    icon = { Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.routines_rotation_label),
                    description = stringResource(R.string.routines_rotation_description),
                    checked = uiState.rotationEnabled,
                    onCheckedChange = viewModel::onRotationToggle
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.routines_rotation_starts_with),
                            style = MaterialTheme.typography.labelLarge
                        )
                        MemberChips(
                            members = uiState.householdMembers,
                            nicknames = uiState.memberNicknames,
                            selected = uiState.assignedTo,
                            onSelect = viewModel::onAssignedToChange
                        )
                    }
                }
            }

            uiState.errorRes?.let { res ->
                Text(
                    text = stringResource(res),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = viewModel::onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = uiState.canSave && !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.routines_add_save),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialMinutes = uiState.reminderMinutes,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.onReminderTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

/** A frequency option with its explanation, selectable like a radio button. */
@Composable
private fun FrequencyOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A day of the week as a round button, in the style of the detail sheet's calendar. */
@Composable
private fun DayCircle(
    label: String,
    dayName: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .toggleable(value = selected, onValueChange = { onToggle() }, role = Role.Checkbox)
            .semantics { contentDescription = dayName },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A settings card with a switch (reminder, turns) and extra content that only appears once it is
 * turned on.
 */
@Composable
private fun SettingCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            AnimatedVisibility(visible = checked) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    expandedContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChips(
    members: List<String>,
    nicknames: Map<String, String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        members.forEach { memberId ->
            FilterChip(
                selected = selected == memberId,
                onClick = { onSelect(memberId) },
                label = {
                    Text(
                        nicknames[memberId]
                            ?: stringResource(R.string.routines_completed_by_unknown)
                    )
                }
            )
        }
    }
}

private val RoutineFrequency.titleRes: Int
    get() = when (this) {
        RoutineFrequency.DAILY -> R.string.routines_frequency_daily
        RoutineFrequency.WEEKLY -> R.string.routines_frequency_weekly
        RoutineFrequency.CUSTOM -> R.string.routines_frequency_custom
        RoutineFrequency.EVERY_N_DAYS -> R.string.routines_frequency_interval
        RoutineFrequency.MONTHLY -> R.string.routines_frequency_monthly
        RoutineFrequency.YEARLY -> R.string.routines_frequency_yearly
    }

private val RoutineFrequency.descRes: Int
    get() = when (this) {
        RoutineFrequency.DAILY -> R.string.routines_frequency_daily_desc
        RoutineFrequency.WEEKLY -> R.string.routines_frequency_weekly_desc
        RoutineFrequency.CUSTOM -> R.string.routines_frequency_custom_desc
        RoutineFrequency.EVERY_N_DAYS -> R.string.routines_frequency_interval_desc
        RoutineFrequency.MONTHLY -> R.string.routines_frequency_monthly_desc
        RoutineFrequency.YEARLY -> R.string.routines_frequency_yearly_desc
    }

/** The day's full name for the screen reader ("lunes", "martes"…). */
private fun Int.toDayOfWeekName(): String {
    val dayOfWeek = if (this == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(this - 1)
    return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
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
