package com.monsteraltech.habitly.feature.routines.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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

/**
 * The share-out of household routines completed this week.
 *
 * Deliberately **not a ranking**: it does not sort by "winner" or paint positions. Leaderboards
 * motivate some and burn out others, and this is a home, not a competition. It shows the split and
 * a cooperative total.
 */
@Composable
fun HouseholdBalanceCard(
    balance: Map<String, Int>,
    memberNicknames: Map<String, String>,
    members: List<String>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val total = balance.values.sum()
    if (total == 0) return

    // The household members' order is kept, not the count's.
    val rows = members.ifEmpty { balance.keys.toList() }
    val max = balance.values.maxOrNull() ?: 0

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.routines_balance_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = pluralStringResource(R.plurals.routines_balance_total, total, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEach { memberId ->
                MemberBalanceRow(
                    name = memberNicknames[memberId]
                        ?: stringResource(R.string.routines_completed_by_unknown),
                    count = balance[memberId] ?: 0,
                    max = max,
                    isCurrentUser = memberId == currentUserId
                )
            }
        }
    }
}

@Composable
private fun MemberBalanceRow(
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
            modifier = Modifier.width(96.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = if (max == 0) 0f else count.toFloat() / max.toFloat()
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
            modifier = Modifier.width(24.dp)
        )
    }
}
