package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AvailableAiModels
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FakeAiAssistantRepository : AiAssistantRepository {

    var shouldFailSendMessage = false
    var errorMessage = "Error simulado"

    private val _activeSession = MutableStateFlow<AiChatSession?>(null)
    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.Ready)
    private val _selectedModel = MutableStateFlow<AiModelConfig>(AvailableAiModels.Gemma4_E2B_IT)
    private val _chatHistory = MutableStateFlow<List<AiChatSession>>(emptyList())

    val savedSessions = mutableListOf<AiChatSession>()
    val deletedSessionIds = mutableListOf<String>()
    var resetSessionCallCount = 0
    var sendMessageCallCount = 0

    override fun getAvailableModels(): List<AiModelConfig> = AvailableAiModels.models

    override fun observeSelectedModel(): Flow<AiModelConfig> = _selectedModel

    override fun selectModel(modelId: String) {
        val config = AvailableAiModels.models.find { it.id == modelId } ?: return
        _selectedModel.value = config
    }

    override fun observeModelStatus(): Flow<ModelStatus> = _modelStatus

    override suspend fun downloadModel() {
        _modelStatus.value = ModelStatus.Ready
    }

    override fun setActiveSession(session: AiChatSession) {
        _activeSession.value = session
    }

    override suspend fun sendMessage(): Flow<String> = flow {
        sendMessageCallCount++
        if (shouldFailSendMessage) {
            throw Exception(errorMessage)
        }
        emit("Respuesta simulada")
    }

    override suspend fun resetSession() {
        resetSessionCallCount++
    }

    override fun isModelLoaded(): Boolean = true

    override fun observeChatHistory(): Flow<List<AiChatSession>> = _chatHistory

    override suspend fun saveSession(session: AiChatSession) {
        savedSessions.add(session)
    }

    override suspend fun getSession(sessionId: String): AiChatSession? {
        return savedSessions.find { it.id == sessionId }
    }

    override suspend fun deleteSession(sessionId: String) {
        deletedSessionIds.add(sessionId)
    }

    fun addSessionToHistory(session: AiChatSession) {
        _chatHistory.value = _chatHistory.value + session
    }

    fun setModelStatus(status: ModelStatus) {
        _modelStatus.value = status
    }

    fun reset() {
        shouldFailSendMessage = false
        errorMessage = "Error simulado"
        savedSessions.clear()
        deletedSessionIds.clear()
        resetSessionCallCount = 0
        sendMessageCallCount = 0
        _activeSession.value = null
        _modelStatus.value = ModelStatus.Ready
        _selectedModel.value = AvailableAiModels.Gemma4_E2B_IT
        _chatHistory.value = emptyList()
    }
}
