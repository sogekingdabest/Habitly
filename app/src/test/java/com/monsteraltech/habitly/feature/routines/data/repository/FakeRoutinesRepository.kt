package com.monsteraltech.habitly.feature.routines.data.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineComment
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

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

    override suspend fun getRoutine(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType
    ): Result<Routine?> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        val list = if (type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        return Result.success(list.value.find { it.id == routineId })
    }

    override suspend fun updateRoutineCompletion(
        userId: String,
        householdId: String,
        routine: Routine,
        completedAt: Long?,
        completedBy: String?
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        updateCompletionCalls++
        val list = if (routine.type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val updated = list.value.map { existing ->
            if (existing.id == routine.id) {
                existing.copy(lastCompletedAt = completedAt, lastCompletedBy = completedBy)
            } else existing
        }
        if (routine.type == RoutineType.PERSONAL) {
            personalRoutines.value = updated
        } else {
            householdRoutines.value = updated
        }
        return Result.success(Unit)
    }

    /** Completados por id de rutina; la clave vacía sirve de comodín para cualquiera. */
    var stubCompletions: Map<String, List<RoutineCompletion>> = emptyMap()

    /** Consultas de historial hechas: sirve para afirmar que no se lee más de lo necesario. */
    var getCompletionsCalls = 0

    override suspend fun getCompletions(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        from: LocalDate,
        to: LocalDate
    ): Result<List<RoutineCompletion>> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        getCompletionsCalls++
        val forRoutine = stubCompletions[routineId] ?: stubCompletions[""] ?: emptyList()
        return Result.success(forRoutine.filter { !it.date.isBefore(from) && !it.date.isAfter(to) })
    }

    var assignments: MutableMap<String, String?> = mutableMapOf()

    override suspend fun updateRoutineAssignment(
        userId: String,
        householdId: String,
        routineId: String,
        type: RoutineType,
        assignedTo: String?
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        assignments[routineId] = assignedTo
        val list = if (type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val updated = list.value.map { if (it.id == routineId) it.copy(assignedTo = assignedTo) else it }
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
        routine: Routine
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        updateRoutineCalls++
        val list = if (routine.type == RoutineType.PERSONAL) personalRoutines else householdRoutines
        val updated = list.value.map { existing ->
            if (existing.id == routine.id) routine else existing
        }
        if (routine.type == RoutineType.PERSONAL) {
            personalRoutines.value = updated
        } else {
            householdRoutines.value = updated
        }
        return Result.success(Unit)
    }

    // ---------- Comentarios ----------

    private val comments = MutableStateFlow<List<RoutineComment>>(emptyList())

    var addCommentCalls = 0
    var deleteCommentCalls = 0

    override fun observeComments(householdId: String, routineId: String): Flow<List<RoutineComment>> =
        comments

    override suspend fun addComment(
        householdId: String,
        routineId: String,
        comment: RoutineComment
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        addCommentCalls++
        comments.value = comments.value + comment
        bumpCommentCount(routineId, +1)
        return Result.success(Unit)
    }

    override suspend fun deleteComment(
        householdId: String,
        routineId: String,
        commentId: String
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        deleteCommentCalls++
        comments.value = comments.value.filterNot { it.id == commentId }
        bumpCommentCount(routineId, -1)
        return Result.success(Unit)
    }

    /** Mirrors the real batch, which keeps the denormalised counter in step with the subcollection. */
    private fun bumpCommentCount(routineId: String, delta: Int) {
        householdRoutines.value = householdRoutines.value.map {
            if (it.id == routineId) it.copy(commentCount = it.commentCount + delta) else it
        }
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
        stubCompletions = emptyMap()
        getCompletionsCalls = 0
        assignments = mutableMapOf()
        shouldFail = false
        errorMessage = "Fake error"
        addRoutineCalls = 0
        updateCompletionCalls = 0
        deleteRoutineCalls = 0
        updateRoutineCalls = 0
        reorderRoutineCalls = 0
    }
}
