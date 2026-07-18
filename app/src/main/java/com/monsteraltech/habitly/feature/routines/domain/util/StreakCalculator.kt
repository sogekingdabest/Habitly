package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.StreakInfo
import java.time.LocalDate

/**
 * Calcula rachas de cumplimiento a partir de las fechas en las que se completó una rutina.
 * Función pura (sin dependencias de Android/Firestore) para poder testearla con JUnit.
 */
object StreakCalculator {

    fun calculate(completedDates: Collection<LocalDate>, today: LocalDate = LocalDate.now()): StreakInfo {
        if (completedDates.isEmpty()) return StreakInfo()

        val days = completedDates.toSortedSet() // distintas y ascendentes
        val total = days.size

        // Mejor racha: el tramo consecutivo más largo.
        var best = 1
        var run = 1
        var previous: LocalDate? = null
        for (day in days) {
            val prev = previous
            if (prev != null) {
                run = if (day == prev.plusDays(1)) run + 1 else 1
                if (run > best) best = run
            }
            previous = day
        }

        // Racha actual: días consecutivos hacia atrás desde el último completado,
        // siempre que ese último sea hoy o ayer (si no, la racha está rota).
        val mostRecent = days.last()
        val current = if (mostRecent == today || mostRecent == today.minusDays(1)) {
            var count = 0
            var cursor = mostRecent
            while (days.contains(cursor)) {
                count++
                cursor = cursor.minusDays(1)
            }
            count
        } else {
            0
        }

        return StreakInfo(current = current, best = best, total = total)
    }
}
