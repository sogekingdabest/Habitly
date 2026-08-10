package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects that a previous attempt to load a model took the process down with it.
 *
 * Running out of memory inside the native engine raises no exception: the kernel kills the app and
 * that is that. From the inside it can only be known *after the fact*, and this is how — a marker
 * is written to disk just before `Engine.initialize()` and cleared on a clean finish. If the marker
 * is still there on the next launch, the previous attempt never returned. Without this the user
 * enters an open-crash-open loop and never learns why.
 *
 * The marker is written with `commit()` on purpose: `apply()` is asynchronous and the process can
 * die before it reaches disk, which is exactly the case being recorded.
 */
@Singleton
class ModelLoadWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    private val tag = "ModelLoadWatchdog"

    /** Consecutive failed attempts recorded for [modelId]. */
    fun failedAttempts(modelId: String): Int =
        sharedPreferences.getInt(attemptsKey(modelId), 0)

    /**
     * Marks the start of a load attempt. Returns how many previous attempts died without reaching
     * [onLoadSucceeded], so the caller can decide whether to degrade or give up.
     */
    @SuppressLint("ApplySharedPref")
    fun onLoadStarting(modelId: String): Int {
        val pending = sharedPreferences.getString(KEY_PENDING_MODEL, null)
        var attempts = failedAttempts(modelId)

        // The previous attempt's marker is still set: that attempt never finished.
        if (pending == modelId) {
            attempts += 1
            Log.w(tag, "El intento anterior de cargar $modelId no terminó (intentos: $attempts). ${lastExitDiagnosis()}")
        }

        sharedPreferences.edit(commit = true) {
            putString(KEY_PENDING_MODEL, modelId)
            putInt(attemptsKey(modelId), attempts)
        }
        return attempts
    }

    /** The engine started: marker and counter are cleared. */
    @SuppressLint("ApplySharedPref")
    fun onLoadSucceeded(modelId: String) {
        sharedPreferences.edit(commit = true) {
            remove(KEY_PENDING_MODEL)
            remove(attemptsKey(modelId))
        }
    }

    /**
     * The load failed with an ordinary exception (corrupt model, bad path). The marker is removed
     * because the process is alive: this is not a silent death and must not count as one.
     */
    @SuppressLint("ApplySharedPref")
    fun onLoadFailedGracefully(modelId: String) {
        sharedPreferences.edit(commit = true) {
            remove(KEY_PENDING_MODEL)
            remove(attemptsKey(modelId))
        }
    }

    /** Forgets a model's history, e.g. when it is deleted and downloaded again. */
    fun reset(modelId: String) = onLoadFailedGracefully(modelId)

    /**
     * Reason for the last abnormal process exit, when the system knows it. It is the only thing
     * that distinguishes "killed by the system for RAM" from an ordinary crash, and it appears in
     * no app log, because by the time it happens there is no app. Requires API 30+.
     *
     * Also checks whether the description mentions `MemoryLimiter`: Android 17 enforces a
     * per-app memory ceiling based on device RAM, and that is the trace it leaves.
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

        /** Trace left by the per-app memory ceiling introduced in Android 17. */
        const val MEMORY_LIMITER = "MemoryLimiter"
    }
}
