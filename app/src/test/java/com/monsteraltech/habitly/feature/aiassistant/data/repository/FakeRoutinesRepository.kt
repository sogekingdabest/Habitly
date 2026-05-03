package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeRoutinesRepository : RoutinesRepository {

    var stubRoutines: List<Routine> = emptyList()

    override fun observePersonalRoutines(userId: String): Flow<List<Routine>> = flowOf(stubRoutines)

    override fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>> = flowOf(emptyList())

    override suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit> = Result.success(Unit)

    override suspend fun updateRoutineCompletion(userId: String, householdId: String, routineId: String, type: RoutineType, completedAt: Long?, completedBy: String?): Result<Unit> = Result.success(Unit)

    override suspend fun deleteRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Unit> = Result.success(Unit)

    override suspend fun updateRoutine(userId: String, householdId: String, routineId: String, type: RoutineType, title: String, description: String, frequency: RoutineFrequency, scheduledDays: List<Int>, reminderTime: Int?): Result<Unit> = Result.success(Unit)

    override suspend fun reorderRoutines(userId: String, householdId: String, type: RoutineType, orderedIds: List<String>): Result<Unit> = Result.success(Unit)

    fun reset() {
        stubRoutines = emptyList()
    }
}
