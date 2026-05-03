package com.monsteraltech.habitly.feature.routines.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.monsteraltech.habitly.R

class RoutineReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val routineTitle = inputData.getString(KEY_ROUTINE_TITLE) ?: return Result.failure()
        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.failure()

        showNotification(routineTitle, routineId)
        return Result.success()
    }

    private fun showNotification(title: String, routineId: String) {
        val channelId = "routines_reminders"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.routines_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.routines_notification_channel_desc)
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

        val notificationText = applicationContext.getString(R.string.routines_notification_text, title)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.routines_notification_title))
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

        fun getUniqueWorkId(routineId: String): String = "routine_reminder_$routineId"
    }
}
