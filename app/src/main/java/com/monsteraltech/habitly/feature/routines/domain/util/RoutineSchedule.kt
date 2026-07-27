package com.monsteraltech.habitly.feature.routines.domain.util

import com.monsteraltech.habitly.feature.routines.domain.model.Routine
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Punto único de verdad para responder "¿esta rutina toca hoy?" y "¿ya está hecha hoy?".
 *
 * Funciones puras (sin Android ni Firestore) para poder testearlas con JUnit y para que
 * la pantalla de rutinas, el dashboard, el widget y el worker de recordatorios respondan
 * exactamente lo mismo en lugar de duplicar la lógica cada uno por su lado.
 */
object RoutineSchedule {

    /**
     * ¿Toca hacerla el día [date]? Tiene en cuenta la pausa, el calendario semanal y,
     * en las rutinas por intervalo, cuánto ha pasado desde la última vez.
     */
    fun isDueOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (isPausedOn(routine, date, zone)) return false
        return when (routine.frequency) {
            RoutineFrequency.EVERY_N_DAYS -> isIntervalDueOn(routine, date, zone)
            else -> matchesDayOfWeek(routine, date)
        }
    }

    /**
     * Solo la parte de calendario semanal, sin pausa ni intervalo. La usa el cálculo de
     * rachas, que necesita saber en qué días del pasado tocaba sin arrastrar el estado
     * actual de la rutina.
     */
    fun matchesDayOfWeek(routine: Routine, date: LocalDate): Boolean {
        val dayOfWeek = date.toCalendarDayOfWeek()
        return when (routine.frequency) {
            RoutineFrequency.DAILY -> true
            RoutineFrequency.WEEKLY -> routine.scheduledDays.contains(dayOfWeek)
            RoutineFrequency.CUSTOM ->
                routine.scheduledDays.isEmpty() || routine.scheduledDays.contains(dayOfWeek)
            RoutineFrequency.EVERY_N_DAYS -> true
        }
    }

    /** ¿Está en modo vacaciones el día [date]? */
    fun isPausedOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val pausedUntil = routine.pausedUntil ?: return false
        val until = Instant.ofEpochMilli(pausedUntil).atZone(zone).toLocalDate()
        return !date.isAfter(until)
    }

    /** ¿La rutina se marcó como completada el día [date]? */
    fun isCompletedOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val lastCompletedAt = routine.lastCompletedAt ?: return false
        return Instant.ofEpochMilli(lastCompletedAt).atZone(zone).toLocalDate() == date
    }

    /** ¿Toca hacerla el día [date] y sigue sin hacerse? */
    fun isPendingOn(
        routine: Routine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = isDueOn(routine, date, zone) && !isCompletedOn(routine, date, zone)

    /**
     * Cuántas veces tocaba hacerla entre [from] y [to], ambos incluidos. Es el denominador de
     * la tasa de cumplimiento.
     *
     * En las rutinas por intervalo se estima a partir del propio intervalo: el "¿tocaba aquel
     * día?" del pasado dependería del historial de completados de entonces, que no reconstruimos.
     */
    fun expectedOccurrences(routine: Routine, from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 0

        val totalDays = ChronoUnit.DAYS.between(from, to) + 1
        if (routine.frequency == RoutineFrequency.EVERY_N_DAYS) {
            val interval = routine.intervalDays?.takeIf { it > 0 } ?: 1
            return ((totalDays + interval - 1) / interval).toInt()
        }

        var count = 0
        var cursor = from
        while (!cursor.isAfter(to)) {
            if (matchesDayOfWeek(routine, cursor)) count++
            cursor = cursor.plusDays(1)
        }
        return count
    }

    /**
     * Una rutina por intervalo toca cuando nunca se ha hecho o cuando desde la última vez
     * han pasado al menos [Routine.intervalDays] días.
     */
    private fun isIntervalDueOn(routine: Routine, date: LocalDate, zone: ZoneId): Boolean {
        val interval = routine.intervalDays?.takeIf { it > 0 } ?: return true
        val lastCompletedAt = routine.lastCompletedAt ?: return true
        val lastDate = Instant.ofEpochMilli(lastCompletedAt).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(lastDate, date) >= interval
    }

    /** `java.time` numera lunes=1..domingo=7; `Calendar` (lo que guarda [Routine]) domingo=1..sábado=7. */
    private fun LocalDate.toCalendarDayOfWeek(): Int = dayOfWeek.value % 7 + 1
}
