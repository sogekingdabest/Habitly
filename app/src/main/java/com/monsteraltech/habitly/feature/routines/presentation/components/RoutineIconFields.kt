package com.monsteraltech.habitly.feature.routines.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel

/**
 * The emoji offered as routine icons, grouped so the sheet is scannable.
 *
 * A closed grid rather than the system keyboard: it keeps the picker short, avoids routines named
 * with arbitrary symbols, and every one of these reads clearly at notification size.
 */
private val ICON_GROUPS: List<Pair<Int, List<String>>> = listOf(
    R.string.routines_icon_group_home to listOf(
        "🧹", "🧽", "🧴", "🧺", "👕", "🛏️", "🚿", "🚽", "🪟", "🗑️"
    ),
    R.string.routines_icon_group_kitchen to listOf(
        "🍽️", "🍳", "🥘", "🛒", "🧊", "☕", "🥦", "🍎"
    ),
    R.string.routines_icon_group_health to listOf(
        "💊", "🏃", "🧘", "💪", "🦷", "💧", "😴", "📖"
    ),
    R.string.routines_icon_group_pets to listOf(
        "🐕", "🐈", "🐟", "🦴", "🐾"
    ),
    R.string.routines_icon_group_other to listOf(
        "🌱", "💰", "📱", "🔧", "🚗", "📅", "🎯", "⭐"
    )
)

/**
 * Routine icon row: shows the current emoji and opens the picker. Also lets it be cleared, since
 * having no icon is a perfectly good choice and the routine renders exactly as it did before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerField(
    icon: String,
    onIconChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { showSheet = true }, modifier = Modifier.weight(1f)) {
            if (icon.isBlank()) {
                Text(stringResource(R.string.routines_icon_none))
            } else {
                Text(text = icon, fontSize = 22.sp)
                Text(
                    text = "  ${stringResource(R.string.routines_icon_label)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (icon.isNotBlank()) {
            IconButton(onClick = { onIconChange("") }) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.routines_icon_clear)
                )
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.routines_icon_choose),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ICON_GROUPS.forEach { (groupRes, emojis) ->
                    Text(
                        text = stringResource(groupRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EmojiGrid(
                        emojis = emojis,
                        selected = icon,
                        onSelect = {
                            onIconChange(it)
                            showSheet = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiGrid(emojis: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (emoji == selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(emoji) }
                    .semantics { contentDescription = emoji },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
    }
}

/**
 * How loudly this routine should alert. Each option maps to its own notification channel; the
 * actual sound and vibration are picked by the user in the system settings (Settings screen).
 */
@Composable
fun NotificationLevelSelector(
    level: NotificationLevel,
    onLevelChange: (NotificationLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        NotificationLevel.SILENT to R.string.routines_level_silent,
        NotificationLevel.DEFAULT to R.string.routines_level_default,
        NotificationLevel.HIGH to R.string.routines_level_high
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, labelRes) ->
            SegmentedButton(
                selected = level == value,
                onClick = { onLevelChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}
