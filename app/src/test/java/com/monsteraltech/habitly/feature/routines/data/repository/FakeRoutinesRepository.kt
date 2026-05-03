package com.monsteraltech.habitly.feature.routines.data.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRoutinesRepository : RoutinesRepository {

    private val personalRoutines = MutableStateFlow<List<Routine>>(emptyList())
    private val householdRoutines = MutableStateFlow<List<Routine>>(emptyList())

    var shouldFail = false
    var errorMessage = "Fake error"

    var addRoutineCalls = 0
    var updateCompletionCalls = 0
    var deleteRoutineCalls = 0
    var updateRoutineCalls = 0
    var reorderRoutineCalls = 0

    override fun observePersonalRoutines(userId: String): Flow<List<Routine>> =
        personalRoutines

    override fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>> =
        householdRoutines

    override suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        addRoutineCalls++
        if (routine.type == RoutineType.PERSONAL) {
            personalRoutines.value += routine
        } else {
            householdRoutines.value += routine
        }
        return Result.success(Unit)
    }

    override suspend fun updateRoutineCompletion(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        completedAt: Long?,
        completedBy: String?
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        updateCompletionCalls++
        val list = if (type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val updated = list.value.map { routine ->
            if (routine.id == routineId) {
                routine.copy(lastCompletedAt = completedAt, lastCompletedBy = completedBy)
            } else routine
        }
        if (type == RoutineType.PERSONAL) {
            personalRoutines.value = updated
        } else {
            householdRoutines.value = updated
        }
        return Result.success(Unit)
    }

    override suspend fun deleteRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        deleteRoutineCalls++
        val list = if (type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val filtered = list.value.filter { it.id != routineId }
        if (type == RoutineType.PERSONAL) {
            personalRoutines.value = filtered
        } else {
            householdRoutines.value = filtered
        }
        return Result.success(Unit)
    }

    override suspend fun updateRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        title: String,
        description: String,
        frequency: RoutineFrequency,
        scheduledDays: List<Int>,
        reminderTime: Int?
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        updateRoutineCalls++
        val list = if (type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val updated = list.value.map { routine ->
            if (routine.id == routineId) {
                routine.copy(
                    title = title.trim(),
                    description = description.trim(),
                    frequency = frequency,
                    scheduledDays = scheduledDays,
                    reminderTime = reminderTime
                )
            } else routine
        }
        if (type == RoutineType.PERSONAL) {
            personalRoutines.value = updated
        } else {
            householdRoutines.value = updated
        }
        return Result.success(Unit)
    }

    override suspend fun reorderRoutines(
        userId: String,
        householdId: String,
        type: RoutineType,
        orderedIds: List<String>
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        reorderRoutineCalls++
        val list = if (type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val updated = orderedIds.mapIndexed { index, id ->
            list.value.find { it.id == id }?.copy(order = index)
        }.filterNotNull()
        if (type == RoutineType.PERSONAL) {
            personalRoutines.value = updated
        } else {
            householdRoutines.value = updated
        }
        return Result.success(Unit)
    }

    fun addPersonalRoutine(routine: Routine) {
        personalRoutines.value += routine
    }

    fun addHouseholdRoutine(routine: Routine) {
        householdRoutines.value += routine
    }

    fun reset() {
        personalRoutines.value = emptyList()
        householdRoutines.value = emptyList()
        shouldFail = false
        errorMessage = "Fake error"
        addRoutineCalls = 0
        updateCompletionCalls = 0
        deleteRoutineCalls = 0
        updateRoutineCalls = 0
        reorderRoutineCalls = 0
    }
}
