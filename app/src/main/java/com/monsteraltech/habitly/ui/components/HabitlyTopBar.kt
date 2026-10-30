package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.ui.theme.habitly
import com.monsteraltech.habitly.ui.theme.sizing

/**
 * The one header of the app.
 *
 * Eight screens had five different headers: three used Material's `TopAppBar`, the rest improvised
 * a `Row` with its own title size, height and margins, and two had no header at all. A screen
 * should not have to decide what a title looks like.
 *
 * [overline] is the small line above the title (a date, a section); [subtitle] the one below it
 * (a count, a state). The title is `headlineSmall` rather than `headlineMedium` so three actions
 * still fit on a narrow screen without it breaking into two lines, which is what made "Lista de
 * la Compra" eat 170px of the shopping screen.
 */
@Composable
fun HabitlyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    titleMaxLines: Int = 2,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onNavigateBack != null) 4.dp else 20.dp, end = 8.dp)
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateBack != null) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(MaterialTheme.sizing.icon),
                )
            }
        }
        // weight: the title yields to the actions instead of pushing them off the screen.
        Column(modifier = Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.habitly.accentText,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.habitly.textSecondary,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            content = actions,
        )
    }
}
