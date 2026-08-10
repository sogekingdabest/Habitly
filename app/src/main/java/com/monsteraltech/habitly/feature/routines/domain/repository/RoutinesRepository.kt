package com.monsteraltech.habitly.feature.routines.domain.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineComment
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RoutinesRepository {
    fun observePersonalRoutines(userId: String): Flow<List<Routine>>
    fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>>
    suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit>

    /**
     * Reads a single routine. It leans on Firestore's offline cache, so it works from a worker
     * without depending on the network.
     */
    suspend fun getRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Routine?>

    /**
     * Records (or removes) the day's completion and recomputes the streak. It needs the whole
     * [Routine], not just its id, because the streak depends on the frequency.
     */
    suspend fun updateRoutineCompletion(userId: String, householdId: String, routine: Routine, completedAt: Long?, completedBy: String?): Result<Unit>

    /** The days the routine was completed within the range, and who did it. */
    suspend fun getCompletions(userId: String, householdId: String, routineId: String, type: RoutineType, from: LocalDate, to: LocalDate): Result<List<RoutineCompletion>>

    /** Deletes the routine and all of its nested completion/comment data. */
    suspend fun deleteRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Unit>

    /**
     * Saves [routine]'s editable fields (title, description, frequency, reminder, pause and
     * rotation). Takes the whole routine rather than a long parameter list.
     */
    suspend fun updateRoutine(userId: String, householdId: String, routine: Routine): Result<Unit>

    /** Advances a rotating routine's turn without touching its other fields. */
    suspend fun updateRoutineAssignment(userId: String, householdId: String, routineId: String, type: RoutineType, assignedTo: String?): Result<Unit>

    suspend fun reorderRoutines(userId: String, householdId: String, type: RoutineType, orderedIds: List<String>): Result<Unit>

    // ---------- Comments (household routines only) ----------

    /**
     * The routine's comments, oldest first, updating live. Firestore's own listener is what makes
     * these real time without any push infrastructure.
     */
    fun observeComments(householdId: String, routineId: String): Flow<List<RoutineComment>>

    /** Adds a comment and bumps the routine's denormalised counter in the same batch. */
    suspend fun addComment(householdId: String, routineId: String, comment: RoutineComment): Result<Unit>

    /** Removes a comment and decrements the counter in the same batch. */
    suspend fun deleteComment(householdId: String, routineId: String, commentId: String): Result<Unit>
}
