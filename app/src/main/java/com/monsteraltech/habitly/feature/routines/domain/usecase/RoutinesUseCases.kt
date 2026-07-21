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

/** Días permitidos en una rutina "cada N días". */
private val INTERVAL_RANGE = 1..365

/** Normaliza el intervalo: solo tiene sentido en [RoutineFrequency.EVERY_N_DAYS]. */
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

        // La rotación solo existe en las rutinas de casa.
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

/** Fechas completadas de una rutina en un rango, para pintar el calendario de la ficha. */
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
 * Pasa el turno de una rutina rotativa al siguiente miembro de la casa.
 *
 * Solo actúa sobre rutinas de casa con la rotación activada; en cualquier otro caso no
 * escribe nada, para que el llamante pueda invocarlo sin comprobar condiciones.
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

/** Devuelve el turno a quien ha desmarcado la rutina (deshacer un completado). */
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
 * Cuántas rutinas de casa ha completado cada miembro en un rango de fechas.
 *
 * Hace una consulta por rutina (N+1). Se asume a propósito: una casa tiene pocas rutinas
 * compartidas, y la alternativa (una *collection group query* sobre `completions`) obligaría
 * a añadir reglas nuevas de Firestore, que es justo lo que este plan evita.
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
