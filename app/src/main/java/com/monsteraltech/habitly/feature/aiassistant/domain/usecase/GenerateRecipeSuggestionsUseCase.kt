package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GenerateRecipeSuggestionsUseCase @Inject constructor(
    private val repository: AiAssistantRepository
) {
    private val systemPrompt = """
Eres un asistente culinario experto en cocina espanola e internacional.
Sugiere recetas basadas en los ingredientes que el usuario tiene disponibles.
Responde en espanol y se conciso.

Formato de respuesta:
- Nombre del plato
- Ingredientes adicionales necesarios (si los hay)
- Pasos breves de preparacion (3-4 pasos maximo)
""".trimIndent()

    suspend operator fun invoke(ingredients: String, chatSession: AiChatSession): Flow<String> {
        val updatedSession = chatSession.copy(systemPrompt = systemPrompt)
            .addUserMessage(ingredients)
        return repository.sendMessage(updatedSession)
    }
}
