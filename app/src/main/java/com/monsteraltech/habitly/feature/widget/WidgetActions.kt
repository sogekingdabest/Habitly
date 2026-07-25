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

/** Id de la línea sobre la que se ha pulsado, único dato que viaja al callback. */
val LineIdKey = ActionParameters.Key<String>("habitly.widget.lineId")

/** Parámetros de una acción del widget para la línea [id]. */
fun widgetLineParameters(id: String): ActionParameters = actionParametersOf(LineIdKey to id)

/**
 * Tacha un producto de la lista de la compra desde el widget, sin abrir la app.
 */
class ToggleShoppingItemAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runWidgetAction(context, glanceId) { it.checkShoppingItem(parameters[LineIdKey].orEmpty()) }
    }
}

/** Da por hecha una rutina de hoy desde el widget. */
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
 * Ejecuta [block] con las dependencias del widget y repinta al terminar.
 *
 * El callback corre fuera de la UI de la app: aquí no hay snackbar donde avisar de un fallo.
 * Por eso se refresca **siempre**, acierte o falle: al acertar la línea desaparece, y al
 * fallar la casilla vuelve a estar sin marcar en lugar de quedarse mintiendo.
 *
 * El tope de tiempo es lo que hace que la acción funcione sin red: la escritura de Firestore
 * no confirma hasta que llega al servidor, pero la caché local ya la tiene aplicada y la
 * mutación queda encolada. Sin el tope, el `await` se quedaría colgado y el widget no se
 * repintaría nunca; además, el receptor que ejecuta el callback tiene su propia ventana
 * limitada antes de que Android lo mate.
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
    }.onFailure { Log.w(TAG, "Acción del widget fallida", it) }

    runCatching { HabitlyWidget().update(context, glanceId) }
}

private const val TAG = "HabitlyWidgetAction"
private const val WRITE_TIMEOUT_MS = 6000L
