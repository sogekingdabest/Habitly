package com.monsteraltech.habitly.feature.routines.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monsteraltech.habitly.feature.routines.data.worker.RoutineCompleteWorker
import com.monsteraltech.habitly.feature.routines.data.worker.RoutineReminderWorker
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import java.util.concurrent.TimeUnit

/**
 * Handles the reminder's action buttons.
 *
 * Both actions hand the real work to WorkManager instead of doing it here: a receiver has about ten
 * seconds and no good place to run coroutines, while the workers already have Hilt access and
 * survive the process being killed. The receiver only dismisses the notification, which has to feel
 * instant.
 */
class RoutineActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        val householdId = intent.getStringExtra(EXTRA_HOUSEHOLD_ID).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE) ?: RoutineType.PERSONAL.name

        RoutineNotifier.cancel(context, routineId)

        val workManager = WorkManager.getInstance(context)

        when (intent.action) {
            ACTION_SNOOZE -> {
                // Re-uses the reminder worker wholesale: it re-reads the routine and checks
                // isPendingOn, so a routine completed during the snooze never nags again.
                val work = OneTimeWorkRequestBuilder<RoutineReminderWorker>()
                    .setInitialDelay(SNOOZE_MINUTES, TimeUnit.MINUTES)
                    .setInputData(
                        workDataOf(
                            RoutineReminderWorker.KEY_ROUTINE_ID to routineId,
                            RoutineReminderWorker.KEY_ROUTINE_TITLE to title,
                            RoutineReminderWorker.KEY_ROUTINE_TYPE to type,
                            RoutineReminderWorker.KEY_USER_ID to userId,
                            RoutineReminderWorker.KEY_HOUSEHOLD_ID to householdId
                        )
                    )
                    .build()
                workManager.enqueueUniqueWork(snoozeWorkId(routineId), ExistingWorkPolicy.REPLACE, work)
            }

            ACTION_DONE -> {
                val work = OneTimeWorkRequestBuilder<RoutineCompleteWorker>()
                    .setInputData(
                        workDataOf(
                            RoutineCompleteWorker.KEY_ROUTINE_ID to routineId,
                            RoutineCompleteWorker.KEY_ROUTINE_TYPE to type,
                            RoutineCompleteWorker.KEY_USER_ID to userId,
                            RoutineCompleteWorker.KEY_HOUSEHOLD_ID to householdId
                        )
                    )
                    .build()
                workManager.enqueueUniqueWork(completeWorkId(routineId), ExistingWorkPolicy.REPLACE, work)
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.monsteraltech.habitly.action.SNOOZE_ROUTINE"
        const val ACTION_DONE = "com.monsteraltech.habitly.action.COMPLETE_ROUTINE"

        /** How long the snooze button pushes the reminder back. */
        const val SNOOZE_MINUTES = 15L

        private const val EXTRA_ROUTINE_ID = "routine_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ICON = "icon"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_HOUSEHOLD_ID = "household_id"

        fun snoozeWorkId(routineId: String): String = "routine_snooze_$routineId"

        fun completeWorkId(routineId: String): String = "routine_complete_$routineId"

        fun pendingIntent(
            context: Context,
            action: String,
            routineId: String,
            title: String,
            icon: String,
            level: NotificationLevel,
            type: RoutineType,
            userId: String,
            householdId: String
        ): PendingIntent {
            val intent = Intent(context, RoutineActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_ROUTINE_ID, routineId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ICON, icon)
                putExtra(EXTRA_LEVEL, level.name)
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_HOUSEHOLD_ID, householdId)
            }
            return PendingIntent.getBroadcast(
                context,
                // The two actions of the same routine must not collapse into one PendingIntent.
                (action + routineId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
