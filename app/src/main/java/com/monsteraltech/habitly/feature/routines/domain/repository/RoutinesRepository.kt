package com.monsteraltech.habitly.feature.routines.domain.repository

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RoutinesRepository {
    fun observePersonalRoutines(userId: String): Flow<List<Routine>>
    fun observeHouseholdRoutines(householdId: String): Flow<List<Routine>>
    suspend fun addRoutine(userId: String, householdId: String, routine: Routine): Result<Unit>

    /**
     * Lee una rutina concreta. Se apoya en la caché offline de Firestore, así que sirve
     * para consultarla desde un worker sin depender de la red.
     */
    suspend fun getRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Routine?>

    /**
     * Registra (o borra) el completado del día y recalcula la racha.
     * Necesita la [Routine] entera, no solo su id, porque la racha depende de la frecuencia.
     */
    suspend fun updateRoutineCompletion(userId: String, householdId: String, routine: Routine, completedAt: Long?, completedBy: String?): Result<Unit>

    /** Días en los que se completó la rutina dentro del rango, con quién la hizo. */
    suspend fun getCompletions(userId: String, householdId: String, routineId: String, type: RoutineType, from: LocalDate, to: LocalDate): Result<List<RoutineCompletion>>

    suspend fun deleteRoutine(userId: String, householdId: String, routineId: String, type: RoutineType): Result<Unit>

    /**
     * Guarda los campos editables de [routine] (título, descripción, frecuencia, recordatorio,
     * pausa y rotación). Recibe la rutina entera en vez de una lista larga de parámetros.
     */
    suspend fun updateRoutine(userId: String, householdId: String, routine: Routine): Result<Unit>

    /** Cambia de turno una rutina rotativa sin tocar el resto de campos. */
    suspend fun updateRoutineAssignment(userId: String, householdId: String, routineId: String, type: RoutineType, assignedTo: String?): Result<Unit>

    suspend fun reorderRoutines(userId: String, householdId: String, type: RoutineType, orderedIds: List<String>): Result<Unit>
}
