package com.monsteraltech.habitly.feature.aiassistant.domain.model

import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency

/**
 * Una rutina propuesta por el asistente de IA.
 * Se extrae del bloque estructurado que el modelo añade al final de sus respuestas.
 *
 * [scheduledDays] usa las constantes de `java.util.Calendar` (domingo=1), igual que
 * [com.monsteraltech.habitly.feature.routines.domain.model.Routine].
 */
data class AiRoutineSuggestion(
    val title: String,
    val description: String = "",
    val frequency: RoutineFrequency = RoutineFrequency.DAILY,
    val scheduledDays: List<Int> = emptyList(),
    val intervalDays: Int? = null
)
