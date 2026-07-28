package com.monsteraltech.habitly.feature.aiassistant.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.data.source.LocalModelManager
import com.monsteraltech.habitly.feature.aiassistant.data.source.NonRetryableDownloadException
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AvailableAiModels
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper
import kotlinx.coroutines.CancellationException

/**
 * Downloads an AI model in the background. Running inside WorkManager as a long-running job with a
 * foreground notification, the download survives navigating between tabs and even a process restart.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val modelManager = LocalModelManager(appContext)

    // Context carrying the language chosen in Settings, which applicationContext does not reflect.
    // `lazy` so it is not rebuilt on every progress refresh during the download.
    private val localizedContext by lazy { LocaleHelper.wrap(applicationContext) }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Falta el identificador del modelo"))
        val config = AvailableAiModels.models.find { it.id == modelId }
            ?: return Result.failure(workDataOf(KEY_ERROR to "Modelo desconocido"))

        return try {
            setForeground(createForegroundInfo(config.name, 0))

            var lastPercent = -1
            modelManager.downloadModel(config) { progress ->
                val percent = (progress * 100).toInt().coerceIn(0, 100)
                // Throttle: notify only when the whole percentage changes.
                if (percent != lastPercent) {
                    lastPercent = percent
                    setProgressAsync(workDataOf(KEY_MODEL_ID to modelId, KEY_PROGRESS to progress))
                    notify(createNotification(config.name, percent))
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NonRetryableDownloadException) {
            // 4xx, integrity, disk space…: retrying will not fix it, so surface the error.
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Error de descarga")))
        } catch (e: Exception) {
            // Transient error (network): retry with backoff. The .tmp is kept, so the next attempt
            // resumes with Range where this one stopped.
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Error de descarga")))
            }
        }
    }

    private fun createForegroundInfo(modelName: String, percent: Int): ForegroundInfo {
        val notification = createNotification(modelName, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(modelName: String, percent: Int): android.app.Notification {
        ensureChannel()
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(localizedContext.getString(R.string.ai_download_notification_title))
            .setContentText(modelName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .build()
    }

    private fun notify(notification: android.app.Notification) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    localizedContext.getString(R.string.ai_download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        /** Unique work name per model, so downloading one does not cancel the other. */
        fun workNameFor(modelId: String): String = "ai_model_download_$modelId"

        /** The shared unique name from earlier versions; cancelled on startup. */
        const val LEGACY_WORK_NAME = "ai_model_download"

        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        /** Total attempts (1 initial + retries) before giving up on a network error. */
        const val MAX_ATTEMPTS = 4

        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 4210
    }
}
