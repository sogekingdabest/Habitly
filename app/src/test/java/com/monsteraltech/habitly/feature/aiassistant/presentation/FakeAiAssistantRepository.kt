package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AvailableAiModels
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FakeAiAssistantRepository : AiAssistantRepository {

    var shouldFailSendMessage = false
    var errorMessage = "Error simulado"

    /** Emite un chunk parcial y se queda colgado hasta que lo cancelen (para testear "parar"). */
    var hangAfterFirstChunk = false

    /** Respuesta que emite el stream (configurable para simular propuestas del asistente). */
    var cannedReply = "Respuesta simulada"

    /** Título que devuelve generateSessionTitle ("" = no se pudo generar). */
    var generatedTitle = ""
    var generateTitleCallCount = 0

    private val _activeSession = MutableStateFlow<AiChatSession?>(null)
    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.Ready)
    private val _selectedModel = MutableStateFlow<AiModelConfig>(AvailableAiModels.Gemma4_E2B_IT)
    private val _chatHistory = MutableStateFlow<List<AiChatSession>>(emptyList())

    val savedSessions = mutableListOf<AiChatSession>()
    val deletedSessionIds = mutableListOf<String>()
    val deletedModelIds = mutableListOf<String>()
    var downloadedModelIds = setOf<String>()
    var cancelDownloadCallCount = 0
    var lastDownloadWifiOnly: Boolean? = null
    var resetSessionCallCount = 0
    var sendMessageCallCount = 0
    var extractRoutinesCallCount = 0
    var extractShoppingCallCount = 0
    var routinesResult = ""
    var shoppingResult = ""
    var summaryResult = ""
    var summarizeCallCount = 0

    override fun getAvailableModels(): List<AiModelConfig> = AvailableAiModels.models

    override fun observeSelectedModel(): Flow<AiModelConfig> = _selectedModel

    override fun selectModel(modelId: String) {
        val config = AvailableAiModels.models.find { it.id == modelId } ?: return
        _selectedModel.value = config
    }

    override fun observeModelStatus(): Flow<ModelStatus> = _modelStatus

    override suspend fun downloadModel(wifiOnly: Boolean) {
        lastDownloadWifiOnly = wifiOnly
        _modelStatus.value = ModelStatus.Ready
    }

    override fun cancelDownload() {
        cancelDownloadCallCount++
    }

    override suspend fun deleteModel(modelId: String) {
        deletedModelIds.add(modelId)
        downloadedModelIds = downloadedModelIds - modelId
    }

    override suspend fun getDownloadedModelIds(): Set<String> = downloadedModelIds

    override fun setActiveSession(session: AiChatSession) {
        _activeSession.value = session
    }

    override suspend fun sendMessage(): Flow<String> = flow {
        sendMessageCallCount++
        if (shouldFailSendMessage) {
            throw Exception(errorMessage)
        }
        if (hangAfterFirstChunk) {
            emit("Respuesta parcial")
            awaitCancellation()
        }
        emit(cannedReply)
    }

    override suspend fun generateSessionTitle(userMessage: String, assistantReply: String): String {
        generateTitleCallCount++
        return generatedTitle
    }

    override suspend fun summarizeConversation(sourceText: String): String {
        summarizeCallCount++
        return summaryResult
    }

    override suspend fun extractRoutines(sourceText: String): String {
        extractRoutinesCallCount++
        return routinesResult
    }

    override suspend fun extractShopping(sourceText: String): String {
        extractShoppingCallCount++
        return shoppingResult
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
        hangAfterFirstChunk = false
        cannedReply = "Respuesta simulada"
        generatedTitle = ""
        generateTitleCallCount = 0
        deletedModelIds.clear()
        downloadedModelIds = setOf()
        cancelDownloadCallCount = 0
        lastDownloadWifiOnly = null
        savedSessions.clear()
        deletedSessionIds.clear()
        resetSessionCallCount = 0
        sendMessageCallCount = 0
        extractRoutinesCallCount = 0
        extractShoppingCallCount = 0
        routinesResult = ""
        shoppingResult = ""
        summaryResult = ""
        summarizeCallCount = 0
        _activeSession.value = null
        _modelStatus.value = ModelStatus.Ready
        _selectedModel.value = AvailableAiModels.Gemma4_E2B_IT
        _chatHistory.value = emptyList()
    }
}
