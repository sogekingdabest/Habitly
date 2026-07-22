package com.monsteraltech.habitly.feature.aiassistant.data.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Canal de salida estructurada del segundo turno vía function-calling. El modelo llama a
 * [addRoutine] una vez por rutina que propone crear y, con constrained decoding activado, el motor
 * garantiza que cada llamada sea estructuralmente válida (adiós al parseo frágil de JSON).
 *
 * La tool **no crea nada**: solo recolecta las propuestas mediante [onRoutineProposed]; la creación
 * real la confirma el usuario desde la tarjeta. Sigue el mismo patrón que las tools de Mobile
 * Actions del Google AI Edge Gallery (constructor con callback + método `@Tool` que devuelve un
 * `Map`).
 *
 * Solo 2 parámetros y ambos String, a propósito: es el "punto dulce" de fiabilidad del
 * tool-calling en modelos on-device pequeños (Gemma 4 E2B) y evita la corrupción numérica en GPU.
 * Los días/intervalo no se piden aquí; se derivan después (una `semanal` sin días se degrada a
 * `diaria`, igual que en la vía por JSON).
 */
class RoutineProposalTools(
    private val onRoutineProposed: (title: String, frequency: String) -> Unit
) : ToolSet {

    @Tool(description = "Registra una rutina propuesta para que el usuario la revise y confirme.")
    fun addRoutine(
        @ToolParam(description = "Título corto que empieza por verbo, p. ej. 'Fregar la cocina'.")
        title: String,
        @ToolParam(description = "Frecuencia de la rutina: 'diaria', 'semanal' o 'cada_n_dias'.")
        frequency: String,
    ): Map<String, String> {
        onRoutineProposed(title, frequency)
        return mapOf("result" to "ok")
    }
}
