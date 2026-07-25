package com.monsteraltech.habitly.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.monsteraltech.habitly.feature.widget.domain.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [WidgetRefresher] sobre Glance.
 *
 * Usa un scope propio de aplicación en vez del `viewModelScope` de quien escribe: el
 * repintado debe completarse aunque la pantalla que provocó el cambio ya se haya ido.
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetRefresher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun refresh() {
        scope.launch {
            runCatching { HabitlyWidget().updateAll(context) }
                .onFailure { Log.w(TAG, "No se pudo refrescar el widget", it) }
        }
    }

    private companion object {
        const val TAG = "GlanceWidgetRefresher"
    }
}
