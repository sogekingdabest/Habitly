package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiQuickPrompt
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.FollowUpSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus

data class AiAssistantUiState(
    val chatSession: AiChatSession = AiChatSession(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    /** El segundo turno (extracción de rutinas o de la compra) está en marcha: muestra "preparando". */
    val isExtractingSuggestions: Boolean = false,
    val error: String? = null,
    /** Evento de un solo uso: nº de productos recién añadidos a la lista (para el snackbar). */
    val addedToListCount: Int? = null,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val availableModels: List<AiModelConfig> = emptyList(),
    val selectedModel: AiModelConfig? = null,
    /** Ids de los modelos del catálogo ya descargados (para el desplegable de modelos). */
    val downloadedModelIds: Set<String> = emptySet(),
    val chatHistory: List<AiChatSession> = emptyList(),
    val quickPrompts: List<AiQuickPrompt> = emptyList(),
    /** Chip de seguimiento tras una propuesta sin tarjeta ("Sí, créalas" / "Sí, a la lista"),
     *  con el destino de la extracción decidido al crearlo. */
    val followUpPrompt: FollowUpSuggestion? = null,
    /** Métricas de la última generación (solo builds debug): TTFT y velocidad de decode. */
    val lastGenerationStats: String? = null,
    /** Productos que la IA propone añadir a la lista, indexados por id de mensaje. */
    val shoppingSuggestions: Map<String, List<AiShoppingSuggestion>> = emptyMap(),
    /** Ids de mensajes cuyas sugerencias ya se añadieron a la lista. */
    val addedSuggestionMessageIds: Set<String> = emptySet(),
    /** Id de mensaje cuya lista se está añadiendo ahora (para el spinner del botón). */
    val addingSuggestionMessageId: String? = null,
    /** Rutinas que la IA propone crear, indexadas por id de mensaje. */
    val routineSuggestions: Map<String, List<AiRoutineSuggestion>> = emptyMap(),
    /** Ids de mensajes cuyas rutinas ya se crearon. */
    val addedRoutineMessageIds: Set<String> = emptySet(),
    /** Id de mensaje cuyas rutinas se están creando ahora. */
    val addingRoutineMessageId: String? = null,
    /** Evento de un solo uso: nº de rutinas recién creadas (para el snackbar). */
    val addedRoutinesCount: Int? = null,
    /** Fracción del presupuesto de contexto ocupada `[0f, 1f]`: dispara el aviso de compactar. */
    val contextUsage: Float = 0f,
    /** La compactación (resumen del contexto antiguo) está en marcha. */
    val isCompacting: Boolean = false,
    /** Evento de un solo uso: la conversación se acaba de compactar (para el snackbar). */
    val contextCompacted: Boolean = false
)
