package com.monsteraltech.habitly.feature.routines.domain.model

enum class RoutineType {
    PERSONAL,
    HOUSEHOLD
}

/**
 * Ojo: Firestore serializa este enum por nombre. Añadir un valor nuevo hace que una versión
 * antigua de la app reviente al leer una rutina que lo use, así que conviene que todos los
 * dispositivos de una casa actualicen a la vez.
 */
enum class RoutineFrequency(val label: String) {
    DAILY("Diaria"),
    WEEKLY("Semanal"),
    CUSTOM("Personalizada"),
    EVERY_N_DAYS("Cada N días")
}

data class Routine(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: RoutineType = RoutineType.PERSONAL,
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val scheduledDays: List<Int> = emptyList(),
    /** Cada cuántos días toca, solo para [RoutineFrequency.EVERY_N_DAYS]. */
    val intervalDays: Int? = null,
    /**
     * Modo vacaciones: mientras no se pase esta fecha, la rutina no toca ni notifica
     * y los días saltados no rompen la racha. Nulo = sin pausa.
     */
    val pausedUntil: Long? = null,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val authorId: String = "",
    val lastCompletedAt: Long? = null,
    val lastCompletedBy: String? = null,
    val reminderTime: Int? = null,
    /** Miembro al que le toca ahora. Solo tiene sentido en rutinas de casa. */
    val assignedTo: String? = null,
    /** Al completarla, el turno pasa automáticamente al siguiente miembro de la casa. */
    val rotationEnabled: Boolean = false,
    /** Racha actual, en ocurrencias programadas (no en días naturales). Denormalizada. */
    val currentStreak: Int = 0,
    /** Mejor racha histórica. Denormalizada. */
    val bestStreak: Int = 0,
    /** La racha actual se mantiene viva gracias al protector (hubo un fallo perdonado). */
    val streakGraceUsed: Boolean = false
)
