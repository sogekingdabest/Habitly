package com.monsteraltech.habitly.feature.aiassistant.domain.repository

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import kotlinx.coroutines.flow.Flow

sealed class ModelStatus {
    data object NotDownloaded : ModelStatus()
    data class Downloading(val progress: Float) : ModelStatus()
    data object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()

    /** The device lacks the RAM for this model: it is neither downloaded nor loaded. */
    data object Unsupported : ModelStatus()

    /**
     * Loading it killed the process (once or more) and it will not be retried blindly. This is the
     * state that breaks the open-crash-open loop.
     */
    data object LoadCrashed : ModelStatus()
}

interface AiAssistantRepository {
    // Model Management
    fun getAvailableModels(): List<AiModelConfig>
    fun observeSelectedModel(): Flow<AiModelConfig>
    fun selectModel(modelId: String)

    /**
     * Total device RAM in bytes, expressed in commercial GB, or 0 if it could not be read. Compared
     * against the [AiModelConfig] thresholds through `compatibilityWith`.
     */
    fun getDeviceRamBytes(): Long

    /** Forgets a model's failed-load history so a clean attempt is allowed. */
    fun clearLoadFailures(modelId: String)

    fun observeModelStatus(): Flow<ModelStatus>

    /** Enqueues the selected model's download. With [wifiOnly], only over unmetered networks. */
    suspend fun downloadModel(wifiOnly: Boolean = false)

    /** Cancels the selected model's download; the partial file is kept so it can resume. */
    fun cancelDownload()

    /** Deletes the given model from disk along with its partial download. If it is the selected
     *  one, the engine is unloaded too and the status returns to "not downloaded". */
    suspend fun deleteModel(modelId: String)

    /** Ids of the catalog models that have a valid file on disk. */
    suspend fun getDownloadedModelIds(): Set<String>

    // Chat Execution
    fun setActiveSession(session: AiChatSession)
    suspend fun sendMessage(): Flow<String>

    /**
     * Isolated "NL-to-Format" second turn (low temperature, never persisted): pulls the proposed
     * routines out of the first turn's text. Uses function-calling with constrained decoding when
     * the model supports it, falling back to JSON. Returns `{"routines":[...]}`, or "" if nothing.
     */
    suspend fun extractRoutines(sourceText: String): String

    /** Like [extractRoutines] but for the shopping list (JSON only). "" if there is nothing. */
    suspend fun extractShopping(sourceText: String): String

    /**
     * Generates a short session title from the first exchange, in an ephemeral low-temperature call.
     * Returns "" on failure, in which case the caller keeps whatever title it already had.
     */
    suspend fun generateSessionTitle(userMessage: String, assistantReply: String): String

    /**
     * Summarises the older part of the conversation in an ephemeral low-temperature call (context
     * compaction). Returns the summary, or "" on failure.
     */
    suspend fun summarizeConversation(sourceText: String): String

    suspend fun resetSession()
    fun isModelLoaded(): Boolean

    // Chat History
    fun observeChatHistory(): Flow<List<AiChatSession>>
    suspend fun saveSession(session: AiChatSession)
    suspend fun getSession(sessionId: String): AiChatSession?
    suspend fun deleteSession(sessionId: String)
}
