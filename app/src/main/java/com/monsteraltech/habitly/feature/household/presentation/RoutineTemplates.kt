package com.monsteraltech.habitly.feature.household.presentation

import androidx.annotation.StringRes
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import java.util.Calendar

/**
 * Una rutina de casa típica, ofrecida al crear la casa.
 *
 * El título es un id de recurso y no texto: se resuelve en el composable, que es el contexto que
 * sigue el idioma de Ajustes. La frecuencia viene precargada con algo sensato para que el usuario
 * no tenga que decidir ocho veces seguidas.
 *
 * [scheduledDays] usa las constantes de `java.util.Calendar` (domingo=1), igual que `Routine`.
 */
data class RoutineTemplate(
    val id: String,
    @StringRes val titleRes: Int,
    val frequency: RoutineFrequency,
    val scheduledDays: List<Int> = emptyList(),
    val intervalDays: Int? = null
)

/**
 * Plantillas que se ofrecen en el onboarding. Ocho: las suficientes para que una casa arranque
 * con algo de verdad y las pocas para que la pantalla no dé pereza.
 *
 * Están repartidas por días a propósito, para que no toque todo el mismo día.
 */
val HOUSEHOLD_ROUTINE_TEMPLATES = listOf(
    RoutineTemplate(
        id = "trash",
        titleRes = R.string.template_trash,
        frequency = RoutineFrequency.DAILY
    ),
    RoutineTemplate(
        id = "dishes",
        titleRes = R.string.template_dishes,
        frequency = RoutineFrequency.DAILY
    ),
    RoutineTemplate(
        id = "laundry",
        titleRes = R.string.template_laundry,
        frequency = RoutineFrequency.WEEKLY,
        scheduledDays = listOf(Calendar.SATURDAY)
    ),
    RoutineTemplate(
        id = "bathroom",
        titleRes = R.string.template_bathroom,
        frequency = RoutineFrequency.WEEKLY,
        scheduledDays = listOf(Calendar.SATURDAY)
    ),
    RoutineTemplate(
        id = "floor",
        titleRes = R.string.template_floor,
        frequency = RoutineFrequency.WEEKLY,
        scheduledDays = listOf(Calendar.WEDNESDAY)
    ),
    RoutineTemplate(
        id = "dust",
        titleRes = R.string.template_dust,
        frequency = RoutineFrequency.WEEKLY,
        scheduledDays = listOf(Calendar.TUESDAY)
    ),
    RoutineTemplate(
        id = "groceries",
        titleRes = R.string.template_groceries,
        frequency = RoutineFrequency.WEEKLY,
        scheduledDays = listOf(Calendar.FRIDAY)
    ),
    RoutineTemplate(
        id = "sheets",
        titleRes = R.string.template_sheets,
        frequency = RoutineFrequency.EVERY_N_DAYS,
        intervalDays = 14
    )
)
