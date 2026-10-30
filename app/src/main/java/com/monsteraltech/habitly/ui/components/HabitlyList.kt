package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly
import com.monsteraltech.habitly.ui.theme.sizing

/**
 * A section of list drawn as ONE card with hairline-separated rows, instead of a card per item.
 *
 * A card per item costs ~92dp a row once its padding, its toggle and the gap to the next card are
 * counted, so four routines filled the screen. The same rows inside a single card take 56dp and
 * seven fit, which is the difference between reading the day at a glance and scrolling for it.
 *
 * Rows go in as [HabitlyRow] / [HabitlyToggleRow] with a [HabitlyRowDivider] between them — the
 * caller keeps control of which rows carry a divider (a group label in between does not).
 */
@Composable
fun HabitlyListSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    shape: Shape = LeafCornerLarge,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .habitlyShadow(MaterialTheme.sizing.elevationRaised, shape)
            .clip(shape)
            .background(MaterialTheme.habitly.card)
            .border(1.dp, MaterialTheme.habitly.cardBorder, shape),
    ) {
        if (title != null || trailing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

/** Hairline between two rows of the same section. Inset so it does not cut the leading slot. */
@Composable
fun HabitlyRowDivider(startInset: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (startInset) 16.dp else 0.dp),
        thickness = 1.dp,
        color = MaterialTheme.habitly.border,
    )
}

/**
 * A list row: optional leading slot, the content, optional trailing slot.
 *
 * The height is a floor, never a fixed value: at 200% font size the text has to be able to push
 * the row taller instead of being clipped.
 */
@Composable
fun HabitlyRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HabitlyRowLayout(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier
        ),
        leading = leading,
        trailing = trailing,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Checkable variant. The whole row is the toggle — a nested clickable inside it would steal the
 * tap and leave TalkBack with two targets for one action.
 *
 * [stateLabel] is what TalkBack reads for the state ("done" / "pending"): without it the row
 * announces Compose's generic "ticked", which says nothing about a routine.
 */
@Composable
fun HabitlyToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    stateLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    HabitlyRowLayout(
        modifier = modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .semantics { stateDescription = stateLabel },
        leading = leading,
        trailing = trailing,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun HabitlyRowLayout(
    modifier: Modifier,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.sizing.rowMin)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f), content = content)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * The second line of a row: the two badges that matter and nothing else.
 *
 * A routine can carry a description, who did it, whether it is paused, whose turn it is, its
 * streak, whether the streak was protected, its comments and its start or end date. Stacked, that
 * is seven lines with the same weight; the rest belongs in the detail sheet.
 */
@Composable
fun HabitlyRowMeta(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
