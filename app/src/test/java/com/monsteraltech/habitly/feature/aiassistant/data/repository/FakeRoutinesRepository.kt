package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineComment
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

class FakeRoutinesRepository : RoutinesRepository {

    var stubRoutines: List<Routine> = emptyList()
    var stubHouseholdRoutines: List<Routine> = emptyList()

    override fun observePersonalRoutines(userId: String): Flow<List<Routine>> = flowOf(stubRoutines)

    override fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>> = flowOf(stubHouseholdRoutines)

    override suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit> = Result.success(Unit)

    override suspend fun getRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Routine?> =
        Result.success((stubRoutines + stubHouseholdRoutines).find { it.id == routineId })

    override suspend fun updateRoutineCompletion(userId: String, householdId: String, routine: Routine, completedAt: Long?, completedBy: String?): Result<Unit> = Result.success(Unit)

    override suspend fun getCompletions(userId: String, householdId: String, routineId: String, type: RoutineType, from: LocalDate, to: LocalDate): Result<List<RoutineCompletion>> = Result.success(emptyList())

    override suspend fun deleteRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Unit> = Result.success(Unit)

    override suspend fun updateRoutine(userId: String, householdId: String, routine: Routine): Result<Unit> = Result.success(Unit)

    override suspend fun updateRoutineAssignment(userId: String, householdId: String, routineId: String, type: RoutineType, assignedTo: String?): Result<Unit> = Result.success(Unit)

    override suspend fun reorderRoutines(userId: String, householdId: String, type: RoutineType, orderedIds: List<String>): Result<Unit> = Result.success(Unit)

    override fun observeComments(householdId: String, routineId: String): Flow<List<RoutineComment>> = flowOf(emptyList())

    override suspend fun addComment(householdId: String, routineId: String, comment: RoutineComment): Result<Unit> = Result.success(Unit)

    override suspend fun deleteComment(householdId: String, routineId: String, commentId: String): Result<Unit> = Result.success(Unit)

    fun reset() {
        stubRoutines = emptyList()
        stubHouseholdRoutines = emptyList()
    }
}
