package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import javax.inject.Inject

class GenerateRecipeSuggestionsUseCase @Inject constructor() {

    operator fun invoke(): String = SYSTEM_PROMPT

    companion object {
        private val SYSTEM_PROMPT = """
Eres un asistente culinario experto en cocina espanola e internacional.
Sugiere recetas basadas en los ingredientes que el usuario tiene disponibles.
Responde en espanol y se conciso.

Formato de respuesta:
- Nombre del plato
- Ingredientes adicionales necesarios (si los hay)
- Pasos breves de preparacion (3-4 pasos maximo)
""".trimIndent()
    }
}
