package com.monsteraltech.habitly.feature.household.presentation

import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariantes del catálogo de plantillas del onboarding. Una plantilla mal formada no rompe la
 * compilación pero sí crea rutinas raras en la casa de un usuario nuevo: una semanal sin días se
 * degrada a diaria por su cuenta (ver `ParseAiRoutinesUseCase`) y una de intervalo sin intervalo
 * acaba tocando todos los días.
 */
class RoutineTemplatesTest {

    @Test
    fun `hay entre seis y ocho plantillas`() {
        assertTrue(HOUSEHOLD_ROUTINE_TEMPLATES.size in 6..8)
    }

    @Test
    fun `los ids no se repiten`() {
        val ids = HOUSEHOLD_ROUTINE_TEMPLATES.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `todas tienen titulo traducible`() {
        assertTrue(HOUSEHOLD_ROUTINE_TEMPLATES.all { it.titleRes != 0 })
    }

    @Test
    fun `las semanales traen al menos un dia valido`() {
        val weekly = HOUSEHOLD_ROUTINE_TEMPLATES.filter { it.frequency == RoutineFrequency.WEEKLY }

        assertTrue(weekly.isNotEmpty())
        weekly.forEach { template ->
            assertTrue(template.scheduledDays.isNotEmpty())
            // Constantes de Calendar: domingo=1 … sábado=7.
            assertTrue(template.scheduledDays.all { it in 1..7 })
        }
    }

    @Test
    fun `las de intervalo traen un intervalo razonable`() {
        HOUSEHOLD_ROUTINE_TEMPLATES
            .filter { it.frequency == RoutineFrequency.EVERY_N_DAYS }
            .forEach { template ->
                val interval = template.intervalDays
                assertTrue(interval != null && interval in 1..365)
            }
    }

    @Test
    fun `las diarias no llevan dias ni intervalo`() {
        HOUSEHOLD_ROUTINE_TEMPLATES
            .filter { it.frequency == RoutineFrequency.DAILY }
            .forEach { template ->
                assertTrue(template.scheduledDays.isEmpty())
                assertTrue(template.intervalDays == null)
            }
    }

    @Test
    fun `el reparto no carga todo en el mismo dia`() {
        val days = HOUSEHOLD_ROUTINE_TEMPLATES
            .filter { it.frequency == RoutineFrequency.WEEKLY }
            .flatMap { it.scheduledDays }

        assertTrue(days.distinct().size >= 2)
    }
}
