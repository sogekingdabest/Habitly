package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import com.monsteraltech.habitly.feature.routines.domain.model.StreakInfo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Calcula rachas de cumplimiento a partir de las fechas en las que se completó una rutina.
 * Funciones puras (sin dependencias de Android/Firestore) para poder testearlas con JUnit.
 *
 * Dos reglas de diseño, ambas pensadas para que la racha no desmotive:
 *
 * 1. **Se cuentan ocurrencias programadas, no días naturales.** Una rutina de los lunes suma
 *    racha lunes tras lunes; los días en los que no tocaba ni suman ni rompen. (Antes se
 *    contaban días consecutivos, así que ninguna rutina no diaria pasaba de racha 1.)
 * 2. **Protector de racha**: se perdonan hasta [DEFAULT_GRACE_MISSES] ocurrencias falladas antes
 *    de dar la racha por rota. El día de hoy nunca cuenta como fallo mientras no termine.
 */
object StreakCalculator {

    /** Fallos perdonados antes de romper la racha. */
    const val DEFAULT_GRACE_MISSES = 1

    /** Tope de días hacia atrás que se recorren, para que el cálculo no crezca sin límite. */
    private const val MAX_LOOKBACK_DAYS = 400L

    /** Punto de entrada: elige la regla que corresponde a la frecuencia de la rutina. */
    fun forRoutine(
        routine: Routine,
        completedDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
        graceMisses: Int = DEFAULT_GRACE_MISSES
    ): StreakInfo {
        if (routine.frequency == RoutineFrequency.EVERY_N_DAYS) {
            val interval = routine.intervalDays?.takeIf { it > 0 } ?: 1
            return calculateByInterval(
                completedDates = completedDates,
                today = today,
                intervalDays = interval,
                graceDays = graceMisses * interval
            )
        }
        return calculate(
            completedDates = completedDates,
            today = today,
            graceMisses = graceMisses,
            isDueOn = { date -> RoutineSchedule.matchesDayOfWeek(routine, date) }
        )
    }

    /**
     * Rachas por calendario: recorre los días en los que tocaba hacerla.
     * Con [isDueOn] por defecto (siempre toca) equivale a contar días naturales.
     */
    fun calculate(
        completedDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
        graceMisses: Int = DEFAULT_GRACE_MISSES,
        isDueOn: (LocalDate) -> Boolean = { true }
    ): StreakInfo {
        val days = completedDates.toSortedSet()
        if (days.isEmpty()) return StreakInfo()

        val earliest = days.first()
        val horizon = maxOf(earliest, today.minusDays(MAX_LOOKBACK_DAYS))

        var current = 0
        var misses = 0
        var cursor = today
        while (!cursor.isBefore(horizon)) {
            if (isDueOn(cursor)) {
                when {
                    days.contains(cursor) -> current++
                    cursor == today -> Unit
                    else -> {
                        misses++
                        if (misses > graceMisses) break
                    }
                }
            }
            cursor = cursor.minusDays(1)
        }

        var best = 0
        var run = 0
        var scan = horizon
        while (!scan.isAfter(today)) {
            if (isDueOn(scan)) {
                when {
                    days.contains(scan) -> {
                        run++
                        if (run > best) best = run
                    }
                    scan == today -> Unit
                    else -> run = 0
                }
            }
            scan = scan.plusDays(1)
        }

        return StreakInfo(
            current = current,
            best = maxOf(best, current),
            total = days.size,
            graceUsed = misses > 0 && current > 0
        )
    }

    /**
     * Rachas por intervalo ("cada N días"): la racha sigue viva mientras el hueco entre
     * completados no supere [intervalDays] más la tolerancia [graceDays].
     */
    fun calculateByInterval(
        completedDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
        intervalDays: Int,
        graceDays: Int = 0
    ): StreakInfo {
        val days = completedDates.toSortedSet()
        if (days.isEmpty()) return StreakInfo()

        val interval = intervalDays.coerceAtLeast(1)
        val allowed = interval + graceDays.coerceAtLeast(0)
        val newestFirst = days.toList().asReversed()

        var current = 0
        var graceUsed = false
        val mostRecent = newestFirst.first()
        if (ChronoUnit.DAYS.between(mostRecent, today) <= allowed) {
            current = 1
            if (ChronoUnit.DAYS.between(mostRecent, today) > interval) graceUsed = true

            var previous = mostRecent
            for (date in newestFirst.drop(1)) {
                val gap = ChronoUnit.DAYS.between(date, previous)
                if (gap > allowed) break
                if (gap > interval) graceUsed = true
                current++
                previous = date
            }
        }

        var best = 1
        var run = 1
        var previous: LocalDate? = null
        for (date in days) {
            val prev = previous
            if (prev != null) {
                run = if (ChronoUnit.DAYS.between(prev, date) <= interval) run + 1 else 1
                if (run > best) best = run
            }
            previous = date
        }

        return StreakInfo(
            current = current,
            best = maxOf(best, current),
            total = days.size,
            graceUsed = graceUsed && current > 0
        )
    }
}
