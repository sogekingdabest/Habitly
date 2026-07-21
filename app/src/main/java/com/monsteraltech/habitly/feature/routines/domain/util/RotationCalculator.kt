package com.monsteraltech.habitly.feature.routines.domain.util

/**
 * Decide a quién le toca el siguiente turno de una rutina rotativa.
 *
 * Función pura para poder testearla. El orden es el de la lista `members` de la casa, que
 * Firestore devuelve estable, así que el turno es predecible para todo el mundo.
 */
object RotationCalculator {

    /**
     * Siguiente miembro tras [current], dando la vuelta al llegar al final.
     *
     * Si nadie tenía el turno, o quien lo tenía ya no está en la casa (le expulsaron o se fue),
     * empieza por el primero: es preferible a dejar la rutina sin dueño.
     */
    fun next(members: List<String>, current: String?): String? {
        if (members.isEmpty()) return null
        val index = members.indexOf(current)
        if (index == -1) return members.first()
        return members[(index + 1) % members.size]
    }
}
