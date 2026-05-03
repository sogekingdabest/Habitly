package com.monsteraltech.habitly.feature.routines.domain.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.flow.Flow

interface RoutinesRepository {
    fun observePersonalRoutines(userId: String): Flow<List<Routine>>
    fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>>
    suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit>
    suspend fun updateRoutineCompletion(userId: String, householdId: String, routineId: String, type: RoutineType, completedAt: Long?, completedBy: String?): Result<Unit>
    suspend fun deleteRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Unit>
    suspend fun updateRoutine(userId: String, householdId: String, routineId: String, type: RoutineType, title: String, description: String, frequency: RoutineFrequency, scheduledDays: List<Int>, reminderTime: Int?): Result<Unit>
    suspend fun reorderRoutines(userId: String, householdId: String, type: RoutineType, orderedIds: List<String>): Result<Unit>
}
