package com.monsteraltech.habitly.feature.routines.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monsteraltech.habitly.feature.routines.data.notification.RoutineNotifier
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
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
            RoutineNotifier.show(
                context = applicationContext,
                routineId = routineId,
                title = fallbackTitle,
                icon = "",
                level = NotificationLevel.DEFAULT,
                type = type,
                userId = userId,
                householdId = householdId
            )
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

        RoutineNotifier.show(
            context = applicationContext,
            routineId = routineId,
            title = routine.title,
            icon = routine.icon,
            level = routine.notificationLevel,
            type = type,
            userId = userId,
            householdId = householdId
        )
        return Result.success()
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
