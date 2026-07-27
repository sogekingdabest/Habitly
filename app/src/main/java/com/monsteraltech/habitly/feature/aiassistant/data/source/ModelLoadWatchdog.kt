package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detecta que un intento anterior de cargar un modelo se llevó por delante el proceso.
 *
 * Quedarse sin memoria en el motor nativo no lanza ninguna excepción: el kernel mata la app y
 * ya está. Desde dentro solo se puede saber *a posteriori*, y así: se deja una marca en disco
 * justo antes de `Engine.initialize()` y se borra al terminar bien. Si al arrancar la marca
 * sigue puesta, el intento anterior no volvió — sin esto el usuario entra en un bucle de
 * abrir-petar-abrir sin enterarse nunca de por qué.
 *
 * La marca se escribe con `commit()` a propósito: `apply()` es asíncrono y el proceso puede
 * morir antes de que llegue al disco, que es justo el caso que queremos registrar.
 */
@Singleton
class ModelLoadWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    private val tag = "ModelLoadWatchdog"

    /** Intentos fallidos consecutivos registrados para [modelId]. */
    fun failedAttempts(modelId: String): Int =
        sharedPreferences.getInt(attemptsKey(modelId), 0)

    /**
     * Marca que empieza un intento de carga. Devuelve cuántos intentos previos murieron sin
     * llegar a [onLoadSucceeded], para que quien llama decida si degradar o rendirse.
     */
    fun onLoadStarting(modelId: String): Int {
        val pending = sharedPreferences.getString(KEY_PENDING_MODEL, null)
        var attempts = failedAttempts(modelId)

        // La marca del intento anterior sigue puesta: aquel intento nunca terminó.
        if (pending == modelId) {
            attempts += 1
            Log.w(tag, "El intento anterior de cargar $modelId no terminó (intentos: $attempts). ${lastExitDiagnosis()}")
        }

        sharedPreferences.edit()
            .putString(KEY_PENDING_MODEL, modelId)
            .putInt(attemptsKey(modelId), attempts)
            .commit()
        return attempts
    }

    /** El engine arrancó: se limpia la marca y el contador. */
    fun onLoadSucceeded(modelId: String) {
        sharedPreferences.edit()
            .remove(KEY_PENDING_MODEL)
            .remove(attemptsKey(modelId))
            .commit()
    }

    /**
     * La carga falló con una excepción normal (modelo corrupto, ruta mala…). Se quita la marca
     * porque el proceso sigue vivo: esto no es una muerte silenciosa y no debe contar como tal.
     */
    fun onLoadFailedGracefully(modelId: String) {
        sharedPreferences.edit()
            .remove(KEY_PENDING_MODEL)
            .remove(attemptsKey(modelId))
            .commit()
    }

    /** Olvida el historial de un modelo (p. ej. al borrarlo y volver a descargarlo). */
    fun reset(modelId: String) = onLoadFailedGracefully(modelId)

    /**
     * Motivo del último cierre anómalo del proceso, si el sistema lo sabe. Es lo único que
     * distingue "lo mató el sistema por RAM" de un crash normal, y no aparece en ningún log
     * de la app porque para cuando ocurre ya no hay app. Requiere API 30+.
     *
     * También mira si la descripción menciona `MemoryLimiter`: Android 17 impone un techo de
     * memoria por app en función de la RAM del dispositivo y ese es el rastro que deja.
     */
    fun lastExitDiagnosis(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Sin diagnóstico (API < 30)"
        val activityManager = context.getSystemService<ActivityManager>()
            ?: return "Sin diagnóstico (no hay ActivityManager)"

        val exit = runCatching {
            activityManager.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull()
        }.getOrNull() ?: return "Sin registro de cierres previos"

        val reason = when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "el sistema lo cerró por falta de memoria"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "fallo en código nativo"
            ApplicationExitInfo.REASON_SIGNALED -> "terminado por señal ${exit.status}"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excesivo de recursos"
            else -> "motivo ${exit.reason}"
        }
        val description = exit.description.orEmpty()
        val memoryLimiter = if (description.contains(MEMORY_LIMITER, ignoreCase = true)) {
            " [tope de memoria del sistema]"
        } else {
            ""
        }
        return "Último cierre: $reason$memoryLimiter (rss ${exit.rss / 1_048_576} MB) $description"
    }

    private fun attemptsKey(modelId: String) = "$KEY_ATTEMPTS_PREFIX$modelId"

    private companion object {
        const val KEY_PENDING_MODEL = "ai_model_load_pending"
        const val KEY_ATTEMPTS_PREFIX = "ai_model_load_failures_"

        /** Rastro que deja el techo de memoria por app que introduce Android 17. */
        const val MEMORY_LIMITER = "MemoryLimiter"
    }
}
