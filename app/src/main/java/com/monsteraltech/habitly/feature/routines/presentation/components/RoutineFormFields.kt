package com.monsteraltech.habitly.feature.routines.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Quick day counts for "every N days", so a yearly cadence is one tap instead of 362. */
private val PRESET_INTERVALS = listOf(7, 15, 30, 90, 365)

private const val MIN_INTERVAL = 1
private const val MAX_INTERVAL = 365

/**
 * Interval control shared by the create screen and the edit dialog: a typeable numeric field (so a
 * big interval no longer needs hundreds of taps), minus/plus steppers, and quick presets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalSelector(
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(intervalDays.toString()) }
    // Keep the field in sync when the value changes from outside (presets, steppers).
    LaunchedEffect(intervalDays) {
        if (text.toIntOrNull() != intervalDays) text = intervalDays.toString()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { onIntervalChange((intervalDays - 1).coerceIn(MIN_INTERVAL, MAX_INTERVAL)) },
                enabled = intervalDays > MIN_INTERVAL
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.routines_interval_less))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    text = digits
                    digits.toIntOrNull()?.let { onIntervalChange(it.coerceIn(MIN_INTERVAL, MAX_INTERVAL)) }
                },
                label = { Text(stringResource(R.string.routines_interval_field_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(170.dp)
            )
            FilledTonalIconButton(
                onClick = { onIntervalChange((intervalDays + 1).coerceIn(MIN_INTERVAL, MAX_INTERVAL)) },
                enabled = intervalDays < MAX_INTERVAL
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.routines_interval_more))
            }
        }
        Text(
            text = stringResource(R.string.routines_interval_presets_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_INTERVALS.forEach { preset ->
                FilterChip(
                    selected = intervalDays == preset,
                    onClick = { onIntervalChange(preset) },
                    label = { Text(preset.toString()) }
                )
            }
        }
    }
}

private enum class DateTarget { START, END }

/**
 * The routine's lifetime window: optional "starts" and "ends" dates, both clearable, with a
 * validation hint when the end falls before the start.
 *
 * Stores epoch millis anchored to the local start-of-day. The Material date picker works in UTC, so
 * the conversions here round-trip through the calendar date to avoid the off-by-one-day that a raw
 * UTC-midnight value would cause in negative-offset time zones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateWindowFields(
    startDate: Long?,
    endDate: Long?,
    onStartChange: (Long?) -> Unit,
    onEndChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf<DateTarget?>(null) }
    val endBeforeStart = startDate != null && endDate != null && endDate < startDate

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(
            label = stringResource(R.string.routines_start_date_label),
            millis = startDate,
            onPick = { picking = DateTarget.START },
            onClear = { onStartChange(null) }
        )
        DateField(
            label = stringResource(R.string.routines_end_date_label),
            millis = endDate,
            onPick = { picking = DateTarget.END },
            onClear = { onEndChange(null) }
        )
        Text(
            text = if (endBeforeStart) stringResource(R.string.routines_end_before_start)
            else stringResource(R.string.routines_dates_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (endBeforeStart) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    picking?.let { target ->
        val current = if (target == DateTarget.START) startDate else endDate
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current?.let { toUtcPickerMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val stored = fromUtcPickerMillis(utc)
                        if (target == DateTarget.START) onStartChange(stored) else onEndChange(stored)
                    }
                    picking = null
                }) { Text(stringResource(R.string.routines_date_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) {
                    Text(stringResource(R.string.routines_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun DateField(
    label: String,
    millis: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text(
                text = if (millis != null) "$label: ${formatRoutineDate(millis)}"
                else "$label: ${stringResource(R.string.routines_date_none)}"
            )
        }
        if (millis != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.routines_date_clear))
            }
        }
    }
}

/**
 * The hint that tells the user which calendar day a monthly/yearly routine will fall on, derived
 * from the anchor (its start date, or today when none is set yet). Renders nothing for other
 * frequencies.
 */
@Composable
fun AnchorHintText(
    frequency: RoutineFrequency,
    anchorMillis: Long,
    modifier: Modifier = Modifier
) {
    val anchor = Instant.ofEpochMilli(anchorMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val text = when (frequency) {
        RoutineFrequency.MONTHLY ->
            stringResource(R.string.routines_monthly_anchor_hint, anchor.dayOfMonth)
        RoutineFrequency.YEARLY -> {
            val monthName = anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            stringResource(R.string.routines_yearly_anchor_hint, "${anchor.dayOfMonth} $monthName")
        }
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Formats a stored routine date (local-start-of-day millis) for display. */
fun formatRoutineDate(storedMillis: Long): String {
    val localDate = Instant.ofEpochMilli(storedMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.format(dateFormatter.withLocale(Locale.getDefault()))
}

/** Stored local-start-of-day millis -> the UTC-midnight millis the Material picker expects. */
private fun toUtcPickerMillis(storedMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(storedMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** The picker's UTC-midnight selection -> stored local-start-of-day millis. */
private fun fromUtcPickerMillis(utcMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
