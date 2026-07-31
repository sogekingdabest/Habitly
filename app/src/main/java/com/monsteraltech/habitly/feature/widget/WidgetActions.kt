package com.monsteraltech.habitly.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import com.monsteraltech.habitly.feature.widget.domain.WidgetActionsUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.withTimeoutOrNull

/** Id of the tapped line, the only piece of data that travels to the callback. */
val LineIdKey = ActionParameters.Key<String>("habitly.widget.lineId")

/** Parameters of a widget action for line [id]. */
fun widgetLineParameters(id: String): ActionParameters = actionParametersOf(LineIdKey to id)

/** Ticks a shopping-list product off from the widget, without opening the app. */
class ToggleShoppingItemAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runWidgetAction(context, glanceId) { it.checkShoppingItem(parameters[LineIdKey].orEmpty()) }
    }
}

/** Marks one of today's routines done from the widget. */
class CompleteRoutineAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runWidgetAction(context, glanceId) { it.completeRoutine(parameters[LineIdKey].orEmpty()) }
    }
}

/**
 * Runs [block] with the widget's dependencies and repaints when it finishes.
 *
 * The callback runs outside the app's UI: there is no snackbar here to report a failure. So it
 * refreshes **always**, whether it succeeds or fails: on success the line disappears, and on failure
 * the checkbox goes back to unchecked instead of sitting there lying.
 *
 * The timeout is what lets the action work without network: a Firestore write does not confirm until
 * it reaches the server, but the local cache has already applied it and the mutation is queued.
 * Without the timeout the `await` would hang and the widget would never repaint; besides, the
 * receiver running the callback has its own limited window before Android kills it.
 */
private suspend fun runWidgetAction(
    context: Context,
    glanceId: GlanceId,
    block: suspend (WidgetActionsUseCase) -> Result<Unit>
) {
    runCatching {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            block(entryPoint.widgetActionsUseCase()).getOrThrow()
        }
    }.onFailure { Log.w(TAG, "Widget action failed", it) }

    runCatching { HabitlyWidget().update(context, glanceId) }
}

private const val TAG = "HabitlyWidgetAction"
private const val WRITE_TIMEOUT_MS = 6000L
