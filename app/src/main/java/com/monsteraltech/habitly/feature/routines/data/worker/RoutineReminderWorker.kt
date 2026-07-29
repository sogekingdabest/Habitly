package com.monsteraltech.habitly.feature.routines.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper
import com.monsteraltech.habitly.feature.settings.data.SettingsRepositoryImpl
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

/**
 * Notifies a routine at its time. The work is a daily periodic job, so each firing has to decide
 * whether it is genuinely due today.
 *
 * It re-reads the routine from Firestore — which resolves from its offline cache, needing no
 * network — rather than trusting what was saved when the reminder was scheduled: the frequency, the
 * pause or the last time done may have changed since, and for "every N days" routines "is it due
 * today?" depends on exactly that last piece of data.
 */
class RoutineReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Master reminders switch (Settings). If it is off, do not disturb.
        if (!SettingsRepositoryImpl.readRemindersEnabled(applicationContext)) return Result.success()

        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.failure()
        val fallbackTitle = inputData.getString(KEY_ROUTINE_TITLE) ?: return Result.failure()
        val userId = inputData.getString(KEY_USER_ID).orEmpty()
        val householdId = inputData.getString(KEY_HOUSEHOLD_ID).orEmpty()
        val type = runCatching {
            RoutineType.valueOf(inputData.getString(KEY_ROUTINE_TYPE) ?: RoutineType.PERSONAL.name)
        }.getOrDefault(RoutineType.PERSONAL)

        val repository = EntryPointAccessors
            .fromApplication(applicationContext, RoutinesEntryPoint::class.java)
            .routinesRepository()

        val routineResult = repository.getRoutine(userId, householdId, routineId, type)

        // Could not be read (no network and no cache): notify with the last thing we knew.
        if (routineResult.isFailure) {
            showNotification(fallbackTitle, routineId)
            return Result.success()
        }

        // Read fine but does not exist: the routine was deleted, the reminder is moot.
        val routine = routineResult.getOrNull() ?: return Result.success()

        // Do not disturb if it is not due today, is paused, or is already done.
        if (!RoutineSchedule.isPendingOn(routine, LocalDate.now())) return Result.success()

        // If the routine is assigned to another member, it is not this user's concern. This is what
        // gives "it's your turn" without needing FCM or a backend.
        val assignedTo = routine.assignedTo
        if (assignedTo != null && userId.isNotBlank() && assignedTo != userId) {
            return Result.success()
        }

        showNotification(routine.title, routineId)
        return Result.success()
    }

    private fun showNotification(title: String, routineId: String) {
        val channelId = "routines_reminders"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Context carrying the language chosen in Settings: applicationContext does not reflect it
        // (only the Activity's does), so without this the texts came out in the system language.
        val ctx = LocaleHelper.wrap(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                ctx.getString(R.string.routines_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = ctx.getString(R.string.routines_notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent().apply {
            setClassName(applicationContext, "com.monsteraltech.habitly.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("routine_id", routineId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            routineId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = ctx.getString(R.string.routines_notification_text, title)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(ctx.getString(R.string.routines_notification_title))
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(routineId.hashCode(), notification)
    }

    companion object {
        const val KEY_ROUTINE_TITLE = "routine_title"
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_ROUTINE_TYPE = "routine_type"
        const val KEY_USER_ID = "user_id"
        const val KEY_HOUSEHOLD_ID = "household_id"

        fun getUniqueWorkId(routineId: String): String = "routine_reminder_$routineId"
    }
}
