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
 * Detects native crashes / OOM process kills during local model loading.
 *
 * Writes a disk flag prior to engine initialization and clears it upon successful load.
 */
@Singleton
class ModelLoadWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    private val tag = "ModelLoadWatchdog"

    /** Returns consecutive failed load attempts for [modelId]. */
    fun failedAttempts(modelId: String): Int =
        sharedPreferences.getInt(attemptsKey(modelId), 0)

    /**
     * Marks model load attempt start. Returns previous uncompleted attempts count.
     */
    fun onLoadStarting(modelId: String): Int {
        val pending = sharedPreferences.getString(KEY_PENDING_MODEL, null)
        var attempts = failedAttempts(modelId)

        if (pending == modelId) {
            attempts += 1
            Log.w(tag, "Previous load attempt for $modelId failed (attempts: $attempts). ${lastExitDiagnosis()}")
        }

        sharedPreferences.edit()
            .putString(KEY_PENDING_MODEL, modelId)
            .putInt(attemptsKey(modelId), attempts)
            .commit()
        return attempts
    }

    /** Clears watchdog pending flags upon successful load. */
    fun onLoadSucceeded(modelId: String) {
        sharedPreferences.edit()
            .remove(KEY_PENDING_MODEL)
            .remove(attemptsKey(modelId))
            .commit()
    }

    /** Clears pending load flag when loading fails with a caught exception. */
    fun onLoadFailedGracefully(modelId: String) {
        sharedPreferences.edit()
            .remove(KEY_PENDING_MODEL)
            .remove(attemptsKey(modelId))
            .commit()
    }

    fun reset(modelId: String) = onLoadFailedGracefully(modelId)

    /**
     * Inspects Android ApplicationExitInfo (API 30+) to diagnose process exit reasons.
     */
    fun lastExitDiagnosis(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "No diagnosis (API < 30)"
        val activityManager = context.getSystemService<ActivityManager>()
            ?: return "No diagnosis (ActivityManager unavailable)"

        val exit = runCatching {
            activityManager.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull()
        }.getOrNull() ?: return "No previous exit record"

        val reason = when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory kill"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_SIGNALED -> "signaled with status ${exit.status}"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
            else -> "exit reason ${exit.reason}"
        }
        val description = exit.description.orEmpty()
        val memoryLimiter = if (description.contains(MEMORY_LIMITER, ignoreCase = true)) {
            " [system memory limiter]"
        } else {
            ""
        }
        return "Last exit: $reason$memoryLimiter (rss ${exit.rss / 1_048_576} MB) $description"
    }

    private fun attemptsKey(modelId: String) = "$KEY_ATTEMPTS_PREFIX$modelId"

    private companion object {
        const val KEY_PENDING_MODEL = "ai_model_load_pending"
        const val KEY_ATTEMPTS_PREFIX = "ai_model_load_failures_"

        /** Rastro que deja el techo de memoria por app que introduce Android 17. */
        const val MEMORY_LIMITER = "MemoryLimiter"
    }
}
