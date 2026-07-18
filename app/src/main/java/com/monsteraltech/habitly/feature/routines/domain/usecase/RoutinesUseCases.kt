package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

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
        reminderTime: Int? = null
    ): Result<Routine> {
        if (title.isBlank()) return Result.failure(Exception("El título no puede estar vacío"))

        val routine = Routine(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            type = type,
            frequency = frequency,
            scheduledDays = scheduledDays,
            reminderTime = reminderTime,
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
            routineId = routine.id,
            type = routine.type,
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
        reminderTime: Int? = routine.reminderTime
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(Exception("El título no puede estar vacío"))

        return repository.updateRoutine(
            userId = userId,
            householdId = householdId,
            routineId = routine.id,
            type = routine.type,
            title = title,
            description = description,
            frequency = frequency,
            scheduledDays = scheduledDays,
            reminderTime = reminderTime
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
