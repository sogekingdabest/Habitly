package com.monsteraltech.habitly.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monsteraltech.habitly.MainActivity
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper
import com.monsteraltech.habitly.feature.widget.domain.WidgetLine
import com.monsteraltech.habitly.feature.widget.domain.WidgetSnapshot
import com.monsteraltech.habitly.feature.widget.domain.WidgetState
import com.monsteraltech.habitly.ui.theme.InkDarkOnSurface
import com.monsteraltech.habitly.ui.theme.InkDarkSecondary
import com.monsteraltech.habitly.ui.theme.InkPrimary
import com.monsteraltech.habitly.ui.theme.InkSecondary
import com.monsteraltech.habitly.ui.theme.Sage
import com.monsteraltech.habitly.ui.theme.SageDark
import com.monsteraltech.habitly.ui.theme.SageDarkAccent
import com.monsteraltech.habitly.ui.theme.SageDarkPrimary
import com.monsteraltech.habitly.ui.theme.SageText
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: shows the pending shopping products and today's uncompleted routines, **with a
 * checkbox to tick them off without opening the app**. Data is read on demand (Firestore's offline
 * cache) every time Android refreshes the widget or the app is opened.
 *
 * "Verde niebla" skin with the same brand tokens as the app (cream card, sage green, count pills),
 * in day/night variants that follow the system dark mode. Texts are resolved through
 * [LocaleHelper.wrap] to honour the language chosen in Settings — otherwise Glance would use the
 * system locale.
 */
class HabitlyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val snapshot = runCatching { entryPoint.buildWidgetSnapshotUseCase().invoke() }
            .getOrDefault(WidgetSnapshot(state = WidgetState.NO_SESSION))

        provideContent {
            WidgetBody(snapshot)
        }
    }
}

/** Widget palette in day/night variants (they follow the system dark mode). */
private object WidgetColors {
    val title = ColorProvider(day = SageText, night = SageDarkAccent)
    val accent = ColorProvider(day = Sage, night = SageDarkPrimary)
    val textPrimary = ColorProvider(day = InkPrimary, night = InkDarkOnSurface)
    val textSecondary = ColorProvider(day = InkSecondary, night = InkDarkSecondary)
    val pillText = ColorProvider(day = SageDark, night = SageDarkAccent)
}

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot) {
    // Localised context: resolves the texts in the language chosen in Settings.
    val context = LocaleHelper.wrap(LocalContext.current)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_bg))
            .padding(16.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Header(context)
        Spacer(GlanceModifier.height(12.dp))

        when (snapshot.state) {
            WidgetState.NO_SESSION, WidgetState.NO_HOUSEHOLD -> {
                Text(
                    text = context.getString(R.string.widget_open_hint),
                    style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp)
                )
            }

            WidgetState.READY -> {
                WidgetSection(
                    context = context,
                    emoji = "🛒",
                    header = context.getString(R.string.widget_shopping),
                    lines = snapshot.pendingItems,
                    emptyText = context.getString(R.string.widget_shopping_empty),
                    maxLines = MAX_SHOPPING_LINES,
                    onCheck = { line ->
                        actionRunCallback<ToggleShoppingItemAction>(widgetLineParameters(line.id))
                    }
                )
                Spacer(GlanceModifier.height(12.dp))
                WidgetSection(
                    context = context,
                    emoji = "☀️",
                    header = context.getString(R.string.widget_routines),
                    lines = snapshot.pendingRoutines,
                    emptyText = context.getString(R.string.widget_routines_empty),
                    maxLines = MAX_ROUTINE_LINES,
                    onCheck = { line ->
                        actionRunCallback<CompleteRoutineAction>(widgetLineParameters(line.id))
                    }
                )
            }
        }
    }
}

@Composable
private fun Header(context: Context) {
    val locale = context.resources.configuration.locales.get(0)
    val date = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(
                color = WidgetColors.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = date,
            style = TextStyle(
                color = WidgetColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun WidgetSection(
    context: Context,
    emoji: String,
    header: String,
    lines: List<WidgetLine>,
    emptyText: String,
    maxLines: Int,
    onCheck: (WidgetLine) -> Action
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$emoji  $header",
                style = TextStyle(
                    color = WidgetColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            if (lines.isNotEmpty()) {
                CountPill(lines.size)
            }
        }
        Spacer(GlanceModifier.height(6.dp))

        if (lines.isEmpty()) {
            Text(
                text = emptyText,
                style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp)
            )
        } else {
            lines.take(maxLines).forEach { line -> ItemRow(line, onCheck) }
            val remaining = lines.size - maxLines
            if (remaining > 0) {
                Text(
                    text = context.getString(R.string.widget_more, remaining),
                    modifier = GlanceModifier.padding(start = 18.dp, top = 2.dp),
                    style = TextStyle(
                        color = WidgetColors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

/**
 * A checkable line. The checkbox is what makes the widget useful: before, it was look-only. Tapping
 * it makes the callback write to Firestore and repaint; there is no local state to keep.
 */
@Composable
private fun ItemRow(line: WidgetLine, onCheck: (WidgetLine) -> Action) {
    CheckBox(
        // Only pending ones are listed, so it always starts unchecked.
        checked = false,
        onCheckedChange = onCheck(line),
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        text = line.label,
        style = TextStyle(color = WidgetColors.textPrimary, fontSize = 13.sp),
        colors = CheckboxDefaults.colors(
            checkedColor = WidgetColors.accent,
            uncheckedColor = WidgetColors.textSecondary
        ),
        maxLines = 1
    )
}

@Composable
private fun CountPill(count: Int) {
    Text(
        text = count.toString(),
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_pill_bg))
            .padding(horizontal = 9.dp, vertical = 2.dp),
        style = TextStyle(
            color = WidgetColors.pillText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

private const val MAX_SHOPPING_LINES = 3
private const val MAX_ROUTINE_LINES = 3
