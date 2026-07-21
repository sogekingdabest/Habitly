package com.monsteraltech.habitly.feature.routines.domain.usecase

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monsteraltech.habitly.feature.routines.data.worker.RoutineReminderWorker
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleReminderUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * [userId] y [householdId] viajan en el trabajo para que el worker pueda releer la rutina
     * al dispararse: su frecuencia, su pausa o su última vez pueden haber cambiado desde que
     * se programó el recordatorio.
     */
    operator fun invoke(routine: Routine, userId: String, householdId: String) {
        val workManager = WorkManager.getInstance(context)
        val workId = RoutineReminderWorker.getUniqueWorkId(routine.id)

        if (routine.reminderTime == null) {
            workManager.cancelUniqueWork(workId)
            return
        }

        val now = Calendar.getInstance()
        val reminderTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, routine.reminderTime / 60)
            set(Calendar.MINUTE, routine.reminderTime % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelay = reminderTime.timeInMillis - now.timeInMillis

        val workRequest = PeriodicWorkRequestBuilder<RoutineReminderWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    RoutineReminderWorker.KEY_ROUTINE_TITLE to routine.title,
                    RoutineReminderWorker.KEY_ROUTINE_ID to routine.id,
                    RoutineReminderWorker.KEY_ROUTINE_TYPE to routine.type.name,
                    RoutineReminderWorker.KEY_USER_ID to userId,
                    RoutineReminderWorker.KEY_HOUSEHOLD_ID to householdId
                )
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            workId,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}

@Singleton
class CancelReminderUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(routineId: String) {
        val workManager = WorkManager.getInstance(context)
        val workId = RoutineReminderWorker.getUniqueWorkId(routineId)
        workManager.cancelUniqueWork(workId)
    }
}
