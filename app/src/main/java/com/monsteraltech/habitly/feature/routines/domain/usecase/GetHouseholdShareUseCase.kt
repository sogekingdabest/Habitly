package com.monsteraltech.habitly.feature.routines.domain.usecase

import com.monsteraltech.habitly.feature.routines.domain.model.HouseholdShareSummary
import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineCompletion
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reparto de las rutinas de casa: quién ha hecho cuántas esta semana, cuántas la semana pasada y
 * cuántos días seguidos lleva la casa completando todo lo que tocaba.
 *
 * Se lee **una sola ventana** de historial por rutina (desde el lunes de la semana pasada, o más
 * atrás si hace falta para la racha) y de ahí salen las tres cifras. Hacerlo con
 * [GetHouseholdBalanceUseCase] dos veces —una por semana— costaría el doble de consultas.
 *
 * Como [GetHouseholdBalanceUseCase], hace una consulta por rutina de casa (N+1). Se asume a
 * propósito: una casa tiene pocas rutinas compartidas, y la alternativa (*collection group query*)
 * exigiría tocar las reglas de Firestore.
 */
class GetHouseholdShareUseCase @Inject constructor(
    private val repository: RoutinesRepository
) {

    suspend operator fun invoke(
        userId: String,
        householdId: String,
        today: LocalDate = LocalDate.now()
    ): Result<HouseholdShareSummary> {
        if (householdId.isBlank()) return Result.success(HouseholdShareSummary())

        return runCatching {
            val routines = repository.observeHouseholdRoutines(householdId).first()
            if (routines.isEmpty()) return@runCatching HouseholdShareSummary()

            val thisMonday = today.with(DayOfWeek.MONDAY)
            val lastMonday = thisMonday.minusWeeks(1)
            val from = minOf(lastMonday, today.minusDays(STREAK_LOOKBACK_DAYS))

            val completionsByRoutine = mutableMapOf<String, List<RoutineCompletion>>()
            for (routine in routines) {
                completionsByRoutine[routine.id] = repository.getCompletions(
                    userId = userId,
                    householdId = householdId,
                    routineId = routine.id,
                    type = routine.type,
                    from = from,
                    to = today
                ).getOrDefault(emptyList())
            }

            val all = completionsByRoutine.values.flatten()

            HouseholdShareSummary(
                thisWeek = all.countByMemberBetween(thisMonday, today),
                lastWeek = all.countByMemberBetween(lastMonday, thisMonday.minusDays(1)),
                houseStreakDays = houseStreak(routines, completionsByRoutine, today),
                hasHouseholdRoutines = true
            )
        }
    }

    /** Completados por miembro dentro del rango (ambos incluidos). */
    private fun List<RoutineCompletion>.countByMemberBetween(
        from: LocalDate,
        to: LocalDate
    ): Map<String, Int> =
        filter { it.userId.isNotBlank() && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .groupingBy { it.userId }
            .eachCount()

    /**
     * Días seguidos, hacia atrás desde hoy, en los que se completó **todo** lo que tocaba en casa.
     *
     * Reglas, alineadas con `StreakCalculator` para que la racha de la casa no desmotive:
     *  - un día en el que no tocaba nada no suma ni rompe (no se puede fallar lo que no toca),
     *  - hoy nunca cuenta como fallo mientras no termine,
     *  - las rutinas "cada N días" quedan fuera del cálculo: saber si tocaban un día del pasado
     *    exigiría reconstruir el historial de completados de entonces (la misma limitación que
     *    documenta `RoutineSchedule.expectedOccurrences`), y exigirlas a diario rompería la
     *    racha de cualquier casa.
     */
    private fun houseStreak(
        routines: List<Routine>,
        completionsByRoutine: Map<String, List<RoutineCompletion>>,
        today: LocalDate
    ): Int {
        val calendarRoutines = routines.filter { it.frequency != RoutineFrequency.EVERY_N_DAYS }
        if (calendarRoutines.isEmpty()) return 0

        val datesByRoutine = completionsByRoutine.mapValues { (_, completions) ->
            completions.map { it.date }.toSet()
        }

        var streak = 0
        var cursor = today
        var scanned = 0L
        while (scanned <= STREAK_LOOKBACK_DAYS) {
            val due = calendarRoutines.filter { routine ->
                RoutineSchedule.matchesDayOfWeek(routine, cursor) &&
                    !RoutineSchedule.isPausedOn(routine, cursor)
            }
            if (due.isNotEmpty()) {
                val allDone = due.all { routine ->
                    datesByRoutine[routine.id]?.contains(cursor) == true
                }
                when {
                    allDone -> streak++
                    cursor == today -> Unit
                    else -> return streak
                }
            }
            cursor = cursor.minusDays(1)
            scanned++
        }
        return streak
    }

    private companion object {
        /** Tope de días hacia atrás que se leen y se recorren para la racha de la casa. */
        const val STREAK_LOOKBACK_DAYS = 30L
    }
}
