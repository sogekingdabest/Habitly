package com.monsteraltech.habitly.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monsteraltech.habitly.MainActivity
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.widget.domain.WidgetSnapshot
import com.monsteraltech.habitly.feature.widget.domain.WidgetState
import dagger.hilt.android.EntryPointAccessors

/**
 * Widget de pantalla de inicio: muestra los productos pendientes de la compra y las
 * rutinas de hoy sin completar. Los datos se leen bajo demanda (caché offline de
 * Firestore) cada vez que Android refresca el widget o se abre la app.
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
            GlanceTheme {
                WidgetBody(snapshot)
            }
        }
    }
}

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(8.dp))

        when (snapshot.state) {
            WidgetState.NO_SESSION, WidgetState.NO_HOUSEHOLD -> {
                Text(
                    text = context.getString(R.string.widget_open_hint),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                )
            }

            WidgetState.READY -> {
                WidgetSection(
                    header = context.getString(R.string.widget_shopping) + " (" + snapshot.pendingItems.size + ")",
                    lines = snapshot.pendingItems,
                    emptyText = context.getString(R.string.widget_shopping_empty),
                    maxLines = MAX_SHOPPING_LINES
                )
                Spacer(GlanceModifier.height(10.dp))
                WidgetSection(
                    header = context.getString(R.string.widget_routines) + " (" + snapshot.pendingRoutines.size + ")",
                    lines = snapshot.pendingRoutines,
                    emptyText = context.getString(R.string.widget_routines_empty),
                    maxLines = MAX_ROUTINE_LINES
                )
            }
        }
    }
}

@Composable
private fun WidgetSection(
    header: String,
    lines: List<String>,
    emptyText: String,
    maxLines: Int
) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier) {
        Text(
            text = header,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (lines.isEmpty()) {
            Text(
                text = emptyText,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
            )
        } else {
            lines.take(maxLines).forEach { line ->
                Text(
                    text = "• $line",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
                )
            }
            val remaining = lines.size - maxLines
            if (remaining > 0) {
                Text(
                    text = context.getString(R.string.widget_more, remaining),
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp)
                )
            }
        }
    }
}

private const val MAX_SHOPPING_LINES = 4
private const val MAX_ROUTINE_LINES = 3
