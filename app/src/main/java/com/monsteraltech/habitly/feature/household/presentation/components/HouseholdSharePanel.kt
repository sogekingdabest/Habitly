package com.monsteraltech.habitly.feature.household.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.HouseholdShareSummary
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.HabitlyPill
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * Household split panel: what each member did this week, how many consecutive days the household
 * completed everything, and how last week went.
 *
 * **The tone is part of the design, not decoration.** In an app used by people who live together,
 * a scoreboard that singles out whoever does least causes real harm. Hence:
 *  - the first and largest number is the **household** total, not the individual one,
 *  - bars follow the household member order, never highest to lowest,
 *  - there are no rankings, no "worst", no red numbers,
 *  - with a single member the comparison disappears entirely, leaving only their progress.
 *
 * [memberIds] arrives in `Household.members` order and [memberNames] may be missing someone who
 * has not opened the app yet: the fallback text is used instead of a blank row.
 */
@Composable
fun HouseholdSharePanel(
    summary: HouseholdShareSummary,
    memberIds: List<String>,
    memberNames: Map<String, String>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    // With no household routines there is no split to talk about.
    if (!summary.hasHouseholdRoutines) return

    val isSolo = memberIds.size <= 1
    val unknownName = stringResource(R.string.household_member_unknown)

    HabitlyCard(
        modifier = modifier.fillMaxWidth(),
        shape = LeafCornerLarge,
        contentPadding = PaddingValues(20.dp)
    ) {
        Text(
            text = stringResource(R.string.household_share_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The household total leads: it is the number that celebrates the shared effort.
            Text(
                text = when {
                    summary.thisWeekTotal == 0 -> stringResource(R.string.household_share_empty)
                    isSolo -> pluralStringResource(
                        R.plurals.household_share_total_solo,
                        summary.thisWeekTotal,
                        summary.thisWeekTotal
                    )
                    else -> pluralStringResource(
                        R.plurals.household_share_total,
                        summary.thisWeekTotal,
                        summary.thisWeekTotal
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.habitly.textSecondary
            )

            if (!isSolo && summary.thisWeekTotal > 0) {
                memberIds.forEach { memberId ->
                    MemberShareRow(
                        name = memberNames[memberId]?.takeIf { it.isNotBlank() } ?: unknownName,
                        count = summary.thisWeek[memberId] ?: 0,
                        max = summary.maxThisWeek,
                        isCurrentUser = memberId == currentUserId
                    )
                }
            }

            // Household streak, in the streak pill (same colours as a routine's): it is a shared
            // achievement, not an individual grade.
            if (summary.houseStreakDays > 0) {
                HabitlyPill(
                    text = pluralStringResource(
                        R.plurals.household_share_streak,
                        summary.houseStreakDays,
                        summary.houseStreakDays
                    ),
                    background = MaterialTheme.habitly.streakBg,
                    contentColor = MaterialTheme.habitly.streakFg
                )
            }

            // Last week, one line and understated: it is context, not a target.
            if (summary.lastWeekTotal > 0) {
                // Resolved outside the loop: stringResource cannot be called from a
                // non-composable lambda.
                val entryFormat = stringResource(R.string.household_share_last_week_entry)
                val lastWeekText = if (isSolo) {
                    stringResource(R.string.household_share_last_week_total, summary.lastWeekTotal)
                } else {
                    val entries = memberIds
                        .filter { (summary.lastWeek[it] ?: 0) > 0 }
                        .joinToString(" · ") { memberId ->
                            val name = memberNames[memberId]?.takeIf { it.isNotBlank() } ?: unknownName
                            entryFormat.format(name, summary.lastWeek[memberId] ?: 0)
                        }
                    stringResource(R.string.household_share_last_week, entries)
                }
                Text(
                    text = lastWeekText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.habitly.textSecondary
                )
            }
        }
    }
}

/**
 * One row of the split: name, bar scaled against the week's maximum, and count. The viewer's own
 * bar uses the brand green and the rest use tertiary — to find yourself at a glance, not to mark
 * who is winning.
 */
@Composable
private fun MemberShareRow(
    name: String,
    count: Int,
    max: Int,
    isCurrentUser: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrentUser) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(92.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.habitly.boxIdle)
        ) {
            val fraction = if (max <= 0) 0f else count.toFloat() / max.toFloat()
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isCurrentUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary
                        )
                )
            }
        }

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(24.dp)
        )
    }
}
