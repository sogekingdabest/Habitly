package com.monsteraltech.habitly.feature.routines.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Completes a routine from the reminder's "done" button, without opening the app.
 *
 * It runs the same two steps the routines screen does — mark it complete, then pass the turn on for
 * rotating household routines — by reusing those use cases, so the notification path can never
 * drift from the in-app one.
 */
class RoutineCompleteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.failure()
        val userId = inputData.getString(KEY_USER_ID).orEmpty()
        val householdId = inputData.getString(KEY_HOUSEHOLD_ID).orEmpty()
        val type = runCatching {
            RoutineType.valueOf(inputData.getString(KEY_ROUTINE_TYPE) ?: RoutineType.PERSONAL.name)
        }.getOrDefault(RoutineType.PERSONAL)

        if (userId.isBlank()) return Result.failure()

        val entryPoint = EntryPointAccessors
            .fromApplication(applicationContext, RoutinesEntryPoint::class.java)

        val routine = entryPoint.routinesRepository()
            .getRoutine(userId, householdId, routineId, type)
            .getOrNull()
            ?: return Result.success() // Deleted meanwhile: nothing to complete.

        // Someone may have ticked it in the app between the notification and this tap.
        if (RoutineSchedule.isCompletedOn(routine, LocalDate.now())) return Result.success()

        val toggled = entryPoint.toggleRoutineUseCase()(userId, householdId, routine, true)
        if (toggled.isFailure) return Result.retry()

        // Rotating household routines hand the turn to the next member. The use case itself ignores
        // routines that do not rotate, so there is no condition to check here.
        val members = if (householdId.isBlank()) {
            emptyList()
        } else {
            entryPoint.observeHouseholdUseCase()(householdId).first()?.members.orEmpty()
        }
        entryPoint.advanceRotationUseCase()(userId, householdId, routine, members)

        return Result.success()
    }

    companion object {
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_ROUTINE_TYPE = "routine_type"
        const val KEY_USER_ID = "user_id"
        const val KEY_HOUSEHOLD_ID = "household_id"
    }
}
