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
 * The share-out of household routines: who has done how many this week, how many last week, and how
 * many days running the household has completed everything that was due.
 *
 * A **single window** of history is read per routine — from last week's Monday, or further back if
 * the streak needs it — and all three figures come out of it. Doing this with
 * [GetHouseholdBalanceUseCase] twice, one per week, would cost twice the queries.
 *
 * Like [GetHouseholdBalanceUseCase], it makes one query per household routine (N+1). That is
 * accepted on purpose: a household has few shared routines, and the alternative — a collection group
 * query — would mean touching the Firestore rules.
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

            // uid → completed days, and day → routines completed that day (for the streak).
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

    /** Completions per member within the range, both ends inclusive. */
    private fun List<RoutineCompletion>.countByMemberBetween(
        from: LocalDate,
        to: LocalDate
    ): Map<String, Int> =
        filter { it.userId.isNotBlank() && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .groupingBy { it.userId }
            .eachCount()

    /**
     * Days running, backwards from today, on which **everything** the household had due got done.
     *
     * Rules, aligned with `StreakCalculator` so the house streak is not demotivating:
     *  - a day with nothing due neither adds nor breaks (you cannot fail what is not due),
     *  - today never counts as a miss until it is over,
     *  - "every N days" routines are left out of the calculation: knowing whether they were due on
     *    a past day would mean reconstructing the completion history of the time (the same limit
     *    `RoutineSchedule.expectedOccurrences` documents), and requiring them daily would break any
     *    household's streak.
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
                    // Today is not over yet: it cannot count as a miss.
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
        /** How many days back are read and scanned for the house streak. */
        const val STREAK_LOOKBACK_DAYS = 30L
    }
}
