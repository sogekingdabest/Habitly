package com.monsteraltech.habitly.feature.aiassistant.data.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Structured-output channel for the second turn, via function-calling. The model calls [addRoutine]
 * once per routine it proposes and, with constrained decoding on, the engine guarantees every call
 * is structurally valid — no more brittle JSON parsing.
 *
 * The tool **creates nothing**: it only collects proposals through [onRoutineProposed], and the user
 * confirms the actual creation from the card. Same shape as the Mobile Actions tools in Google's AI
 * Edge Gallery: callback in the constructor, `@Tool` method returning a `Map`.
 *
 * Two parameters, both String, on purpose: that is the reliability sweet spot for tool-calling on
 * small on-device models (Gemma 4 E2B) and it sidesteps numeric corruption on GPU. Days and interval
 * are not asked for here, so a `semanal` proposal reaching the card through this path still has no
 * days and still degrades to `diaria` — the JSON path no longer does, since its schema carries both
 * fields. This path only runs for a model that declares `supportsToolCalling`, which the
 * recommended model (LFM3) does not, and widening a tool signature is a different risk from
 * widening a schema: the engine builds the call from the parameter list, where a small model's
 * reliability falls off with each one added.
 *
 * The descriptions below stay in Spanish because they are prompt text the model reads, not
 * user-facing strings — same contract as the markers in `AiStructuredBlocks`.
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
