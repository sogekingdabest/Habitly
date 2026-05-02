package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus

data class AiAssistantUiState(
    val chatSession: AiChatSession = AiChatSession(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val availableModels: List<AiModelConfig> = emptyList(),
    val selectedModel: AiModelConfig? = null,
    val chatHistory: List<AiChatSession> = emptyList()
)
