package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.RotationCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Allowed day counts in an "every N days" routine. */
private val INTERVAL_RANGE = 1..365

/** Normalises the interval: it only makes sense for [RoutineFrequency.EVERY_N_DAYS]. */
private fun sanitizeInterval(frequency: RoutineFrequency, intervalDays: Int?): Int? =
    if (frequency == RoutineFrequency.EVERY_N_DAYS) {
        (intervalDays ?: INTERVAL_RANGE.first).coerceIn(INTERVAL_RANGE)
    } else {
        null
    }

class ObserveRoutinesUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    operator fun invoke(userId: String, householdId: String): Flow<List<Routine>> {
        val personalFlow = repository.observePersonalRoutines(userId)
        val householdFlow = repository.observeHouseholdRoutines(householdId)

        return combine(personalFlow, householdFlow) { personal, household ->
            personal + household
        }
    }
}

class AddRoutineUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        title: String,
        description: String,
        type: RoutineType,
        frequency: RoutineFrequency = RoutineFrequency.DAILY,
        scheduledDays: List<Int> = emptyList(),
        reminderTime: Int? = null,
        intervalDays: Int? = null,
        rotationEnabled: Boolean = false,
        assignedTo: String? = null
    ): Result<Routine> {
        if (title.isBlank()) return Result.failure(Exception("El título no puede estar vacío"))

        // Rotation only exists on household routines.
        val rotates = rotationEnabled && type == RoutineType.HOUSEHOLD

        val routine = Routine(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            type = type,
            frequency = frequency,
            scheduledDays = scheduledDays,
            intervalDays = sanitizeInterval(frequency, intervalDays),
            reminderTime = reminderTime,
            rotationEnabled = rotates,
            assignedTo = if (rotates) assignedTo else null,
            authorId = userId
        )
        return repository.addRoutine(userId, householdId, routine).map { routine }
    }
}

class ToggleRoutineUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        routine: Routine,
        isCompleted: Boolean
    ): Result<Unit> {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        val completedBy = if (isCompleted) userId else null

        return repository.updateRoutineCompletion(
            userId = userId,
            householdId = householdId,
            routine = routine,
            completedAt = completedAt,
            completedBy = completedBy
        )
    }
}

class DeleteRoutineUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        routine: Routine
    ): Result<Unit> {
        return repository.deleteRoutine(
            userId = userId,
            householdId = householdId,
            routineId = routine.id,
            type = routine.type
        )
    }
}

class UpdateRoutineUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        routine: Routine,
        title: String,
        description: String,
        frequency: RoutineFrequency = routine.frequency,
        scheduledDays: List<Int> = routine.scheduledDays,
        reminderTime: Int? = routine.reminderTime,
        intervalDays: Int? = routine.intervalDays,
        pausedUntil: Long? = routine.pausedUntil,
        rotationEnabled: Boolean = routine.rotationEnabled,
        assignedTo: String? = routine.assignedTo
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(Exception("El título no puede estar vacío"))

        return repository.updateRoutine(
            userId = userId,
            householdId = householdId,
            routine = routine.copy(
                title = title.trim(),
                description = description.trim(),
                frequency = frequency,
                scheduledDays = scheduledDays,
                reminderTime = reminderTime,
                intervalDays = sanitizeInterval(frequency, intervalDays),
                pausedUntil = pausedUntil,
                rotationEnabled = rotationEnabled,
                assignedTo = assignedTo
            )
        )
    }
}

class ReorderRoutineUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        type: RoutineType,
        orderedIds: List<String>
    ): Result<Unit> {
        return repository.reorderRoutines(userId, householdId, type, orderedIds)
    }
}

/** A routine's completed dates within a range, for painting the detail sheet's calendar. */
class GetRoutineCompletionsUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        routine: Routine,
        from: LocalDate,
        to: LocalDate
    ): Result<List<LocalDate>> {
        return repository.getCompletions(
            userId = userId,
            householdId = householdId,
            routineId = routine.id,
            type = routine.type,
            from = from,
            to = to
        ).map { completions -> completions.map { it.date } }
    }
}

/**
 * Passes a rotating routine's turn to the next household member.
 *
 * It only acts on household routines with rotation enabled; anything else writes nothing, so the
 * caller can invoke it without checking conditions first.
 */
class AdvanceRotationUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        routine: Routine,
        members: List<String>
    ): Result<String?> {
        if (routine.type != RoutineType.HOUSEHOLD || !routine.rotationEnabled) {
            return Result.success(routine.assignedTo)
        }

        val next = RotationCalculator.next(members, routine.assignedTo)
            ?: return Result.success(routine.assignedTo)

        return repository.updateRoutineAssignment(
            userId = userId,
            householdId = householdId,
            routineId = routine.id,
            type = routine.type,
            assignedTo = next
        ).map { next }
    }
}

/** Returns the turn to whoever unmarked the routine (undoing a completion). */
class ReturnRotationUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        routine: Routine
    ): Result<Unit> {
        if (routine.type != RoutineType.HOUSEHOLD || !routine.rotationEnabled) {
            return Result.success(Unit)
        }
        return repository.updateRoutineAssignment(
            userId = userId,
            householdId = householdId,
            routineId = routine.id,
            type = routine.type,
            assignedTo = userId
        )
    }
}

/**
 * How many household routines each member has completed within a date range.
 *
 * Makes one query per routine (N+1). Accepted on purpose: a household has few shared routines, and
 * the alternative — a collection group query over `completions` — would force new Firestore rules,
 * which is exactly what this plan avoids.
 */
class GetHouseholdBalanceUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {
    suspend operator fun invoke(
        userId: String,
        householdId: String,
        from: LocalDate,
        to: LocalDate
    ): Result<Map<String, Int>> {
        if (householdId.isBlank()) return Result.success(emptyMap())

        return runCatching {
            val routines = repository.observeHouseholdRoutines(householdId).first()
            val counts = mutableMapOf<String, Int>()

            for (routine in routines) {
                val completions = repository.getCompletions(
                    userId = userId,
                    householdId = householdId,
                    routineId = routine.id,
                    type = routine.type,
                    from = from,
                    to = to
                ).getOrDefault(emptyList())

                for (completion in completions) {
                    if (completion.userId.isBlank()) continue
                    counts[completion.userId] = (counts[completion.userId] ?: 0) + 1
                }
            }
            counts.toMap()
        }
    }
}
