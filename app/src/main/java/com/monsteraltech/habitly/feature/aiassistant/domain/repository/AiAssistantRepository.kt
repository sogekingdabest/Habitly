package com.monsteraltech.habitly.feature.aiassistant.domain.repository

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import kotlinx.coroutines.flow.Flow

sealed class ModelStatus {
    data object NotDownloaded : ModelStatus()
    data class Downloading(val progress: Float) : ModelStatus()
    data object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

interface AiAssistantRepository {
    // Model Management
    fun getAvailableModels(): List<AiModelConfig>
    fun observeSelectedModel(): Flow<AiModelConfig>
    fun selectModel(modelId: String)
    
    fun observeModelStatus(): Flow<ModelStatus>
    suspend fun downloadModel()

    // Chat Execution
    fun setActiveSession(session: AiChatSession)
    suspend fun sendMessage(): Flow<String>
    suspend fun resetSession()
    fun isModelLoaded(): Boolean

    // Chat History
    fun observeChatHistory(): Flow<List<AiChatSession>>
    suspend fun saveSession(session: AiChatSession)
    suspend fun getSession(sessionId: String): AiChatSession?
    suspend fun deleteSession(sessionId: String)
}
