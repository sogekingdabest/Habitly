package com.monsteraltech.habitly.feature.household.presentation

import androidx.annotation.StringRes
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import java.util.Calendar

/**
 * A typical household routine, offered when the household is created.
 *
 * The title is a resource id rather than text: it is resolved in the composable, the context that
 * follows the language chosen in Settings. The frequency is preloaded with something sensible so
 * the user does not have to decide eight times in a row.
 *
 * [scheduledDays] uses `java.util.Calendar` constants (Sunday=1), like `Routine`.
 */
data class RoutineTemplate(
    val id: String,
    @StringRes val titleRes: Int,
    val frequency: RoutineFrequency,
    val scheduledDays: List<Int> = emptyList(),
    val intervalDays: Int? = null
)

/**
 * Templates offered during onboarding. Eight of them: enough for a household to start with
 * something real, few enough that the screen is not daunting. Spread across days on purpose, so
 * everything does not land on the same one.
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
