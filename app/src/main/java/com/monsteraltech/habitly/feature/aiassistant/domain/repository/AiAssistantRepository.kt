package com.monsteraltech.habitly.feature.aiassistant.domain.repository

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import kotlinx.coroutines.flow.Flow

sealed class ModelStatus {
    data object NotDownloaded : ModelStatus()
    data class Downloading(val progress: Float) : ModelStatus()
    data object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()

    /** El dispositivo no tiene RAM para este modelo: no se descarga ni se intenta cargar. */
    data object Unsupported : ModelStatus()

    /**
     * Cargarlo mató el proceso (una o más veces) y no se va a reintentar a ciegas. Es el
     * estado que evita el bucle abrir-petar-abrir.
     */
    data object LoadCrashed : ModelStatus()
}

interface AiAssistantRepository {
    // Model Management
    fun getAvailableModels(): List<AiModelConfig>
    fun observeSelectedModel(): Flow<AiModelConfig>
    fun selectModel(modelId: String)

    /**
     * RAM total del dispositivo en GB comerciales (bytes), o 0 si no se pudo leer. Se compara
     * con los umbrales de [AiModelConfig] vía `compatibilityWith`.
     */
    fun getDeviceRamBytes(): Long

    /** Olvida el historial de cargas fallidas de un modelo para permitir un intento limpio. */
    fun clearLoadFailures(modelId: String)

    fun observeModelStatus(): Flow<ModelStatus>

    /** Encola la descarga del modelo seleccionado. Con [wifiOnly], solo por redes sin tarifa. */
    suspend fun downloadModel(wifiOnly: Boolean = false)

    /** Cancela la descarga del modelo seleccionado (lo parcial se conserva para reanudar). */
    fun cancelDownload()

    /** Borra del disco el modelo indicado (y su descarga parcial). Si es el seleccionado,
     *  descarga también el engine y el estado vuelve a "no descargado". */
    suspend fun deleteModel(modelId: String)

    /** Ids de los modelos del catálogo con fichero válido en disco. */
    suspend fun getDownloadedModelIds(): Set<String>

    // Chat Execution
    fun setActiveSession(session: AiChatSession)
    suspend fun sendMessage(): Flow<String>

    /**
     * Segundo turno "NL-to-Format" aislado (baja temperatura, no se persiste): extrae del texto del
     * primer turno las rutinas propuestas. Usa function-calling con constrained decoding si el
     * modelo lo soporta, con fallback a JSON. Devuelve `{"routines":[...]}` o "" si no hay nada.
     */
    suspend fun extractRoutines(sourceText: String): String

    /** Igual que [extractRoutines] pero para la lista de la compra (por JSON). "" si no hay nada. */
    suspend fun extractShopping(sourceText: String): String

    /**
     * Genera un título corto para la sesión a partir del primer intercambio (llamada efímera
     * a baja temperatura). Devuelve "" si no se pudo: el caller conserva el título que haya.
     */
    suspend fun generateSessionTitle(userMessage: String, assistantReply: String): String

    /**
     * Resume la parte antigua de la conversación en una llamada efímera a baja temperatura
     * (compactación de contexto). Devuelve el resumen o "" si no se pudo.
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
