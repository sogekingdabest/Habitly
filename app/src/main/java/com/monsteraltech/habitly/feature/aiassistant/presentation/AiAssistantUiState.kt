package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.QuickPrompt

data class AiAssistantUiState(
    val chatSession: AiChatSession = AiChatSession(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
    /** Evento de un solo uso: nº de productos recién añadidos a la lista (para el snackbar). */
    val addedToListCount: Int? = null,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val availableModels: List<AiModelConfig> = emptyList(),
    val selectedModel: AiModelConfig? = null,
    val chatHistory: List<AiChatSession> = emptyList(),
    val quickPrompts: List<QuickPrompt> = emptyList(),
    /** Productos que la IA propone añadir a la lista, indexados por id de mensaje. */
    val shoppingSuggestions: Map<String, List<AiShoppingSuggestion>> = emptyMap(),
    /** Ids de mensajes cuyas sugerencias ya se añadieron a la lista. */
    val addedSuggestionMessageIds: Set<String> = emptySet(),
    /** Id de mensaje cuya lista se está añadiendo ahora (para el spinner del botón). */
    val addingSuggestionMessageId: String? = null
)
