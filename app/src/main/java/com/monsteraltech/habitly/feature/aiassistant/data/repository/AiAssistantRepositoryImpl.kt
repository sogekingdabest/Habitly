package com.monsteraltech.habitly.feature.aiassistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Content
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monsteraltech.habitly.feature.aiassistant.data.source.LocalModelManager
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiChatDao
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiChatSessionEntity
import com.monsteraltech.habitly.feature.aiassistant.data.worker.ModelDownloadWorker
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AvailableAiModels
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAssistantRepositoryImpl @Inject constructor(
    private val localModelManager: LocalModelManager,
    private val aiChatDao: AiChatDao,
    private val sharedPreferences: SharedPreferences,
    @ApplicationContext private val context: Context
) : AiAssistantRepository {

    private val tag = "AiAssistantRepository"

    private val _selectedModel = MutableStateFlow(getSavedModel())
    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    private val _activeSession = MutableStateFlow<AiChatSession?>(null)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationHistoryKey: String? = null

    // Scope de aplicación (vive mientras exista el singleton), independiente del
    // ciclo de vida de los ViewModels.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    init {
        checkModelStatus(_selectedModel.value)
        // Limpieza de modelos legacy y observación de descargas fuera del hilo principal.
        repoScope.launch {
            localModelManager.cleanupLegacyModels()
        }
        observeDownloadWork()
    }

    private fun getSavedModel(): AiModelConfig {
        val savedId = sharedPreferences.getString("selected_model_id", AvailableAiModels.Gemma4_E2B_IT.id)
        return AvailableAiModels.models.find { it.id == savedId } ?: AvailableAiModels.Gemma4_E2B_IT
    }

    override fun getAvailableModels(): List<AiModelConfig> = AvailableAiModels.models

    override fun observeSelectedModel(): Flow<AiModelConfig> = _selectedModel.asStateFlow()

    override fun selectModel(modelId: String) {
        val config = AvailableAiModels.models.find { it.id == modelId } ?: return
        if (_selectedModel.value.id == config.id) return

        sharedPreferences.edit().putString("selected_model_id", config.id).apply()
        _selectedModel.value = config

        conversationHistoryKey = null
        unload()
        checkModelStatus(config)
    }

    override fun observeModelStatus(): Flow<ModelStatus> = _modelStatus.asStateFlow()

    private fun checkModelStatus(config: AiModelConfig) {
        val path = localModelManager.getModelPath(config)
        if (path != null) {
            _modelStatus.value = ModelStatus.Ready
        } else {
            _modelStatus.value = ModelStatus.NotDownloaded
        }
    }

    override suspend fun downloadModel() {
        val config = _selectedModel.value
        _modelStatus.value = ModelStatus.Downloading(0f)

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to config.id))
            // Cualquier red conectada. Cambiar a NetworkType.UNMETERED para
            // restringir a Wi-Fi si se quiere evitar consumo de datos móviles.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // KEEP: si ya hay una descarga en curso no la reiniciamos.
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Refleja el estado del worker de descarga en [_modelStatus]. Vive en el scope
     * de aplicación, así que sigue actualizando aunque el usuario cambie de pantalla.
     */
    private fun observeDownloadWork() {
        repoScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val info = infos.lastOrNull() ?: return@collect
                    // Ignoramos progreso de un modelo distinto al seleccionado.
                    val workModelId = info.progress.getString(ModelDownloadWorker.KEY_MODEL_ID)
                    val isForSelected = workModelId == null || workModelId == _selectedModel.value.id

                    when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> {
                            if (isForSelected) _modelStatus.value = ModelStatus.Downloading(0f)
                        }
                        WorkInfo.State.RUNNING -> {
                            if (isForSelected) {
                                val progress = info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                                _modelStatus.value = ModelStatus.Downloading(progress)
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> checkModelStatus(_selectedModel.value)
                        WorkInfo.State.CANCELLED -> checkModelStatus(_selectedModel.value)
                        WorkInfo.State.FAILED -> {
                            val message = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                                ?: "Error de descarga"
                            _modelStatus.value = ModelStatus.Error(message)
                        }
                    }
                }
        }
    }

    override fun setActiveSession(session: AiChatSession) {
        _activeSession.value = session
    }

    override suspend fun sendMessage(): Flow<String> = flow<String> {
        val session = _activeSession.value
            ?: throw IllegalStateException("No hay sesión activa. Llama a setActiveSession primero.")

        if (engine == null) {
            loadModel(session)
        }

        val sessionNow = _activeSession.value ?: session

        // If the session has changed, recreate the conversation with correct history and system instruction.
        if (sessionNow.id != conversationHistoryKey || conversation == null) {
            conversationHistoryKey = sessionNow.id
            recreateConversation(sessionNow)
        }

        val conv = conversation
            ?: throw IllegalStateException("Modelo no cargado")

        // Get the last USER message
        val prompt = sessionNow.messages
            .lastOrNull { it.role == com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole.User }
            ?.content ?: ""

        Log.d(tag, "Sending prompt to LiteRT-LM: $prompt")

        conv.sendMessageAsync(prompt)
            .collect { message ->
                emit(message.toString())
            }
    }.flowOn(Dispatchers.Default)

    override suspend fun resetSession() {
        withContext(Dispatchers.Default) {
            conversationHistoryKey = null
            recreateConversation(_activeSession.value)
        }
    }

    override fun isModelLoaded(): Boolean = engine != null

    private suspend fun loadModel(session: AiChatSession? = null) {
        withContext(Dispatchers.IO) {
            try {
                val config = _selectedModel.value
                val modelPath = localModelManager.getModelPath(config)
                    ?: throw IllegalStateException("Modelo no descargado")

                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path
                )

                engine = Engine(engineConfig)
                engine!!.initialize()

                createConversation(session)
                Log.d(tag, "LiteRT-LM engine loaded from: $modelPath")
                _modelStatus.value = ModelStatus.Ready
            } catch (e: Exception) {
                Log.e(tag, "Error loading LiteRT-LM model", e)
                _modelStatus.value = ModelStatus.Error(
                    "Error al cargar el modelo: ${e.message}"
                )
            }
        }
    }

    private fun createConversation(session: AiChatSession? = null) {
        val eng = engine ?: return

        // Use session's system prompt
        val systemPrompt = session?.systemPrompt?.takeIf { it.isNotBlank() }
        
        // Map domain messages to LiteRT-LM messages for history
        val history = session?.messages?.dropLast(1)?.mapNotNull { msg ->
            when (msg.role) {
                com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole.User -> Message.user(msg.content)
                com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole.Assistant -> Message.model(msg.content)
                else -> null
            }
        } ?: emptyList()

        val configBuilder = ConversationConfig(
            samplerConfig = SamplerConfig(
                topK = 64,
                topP = 0.95,
                temperature = 0.9
            ),
            systemInstruction = systemPrompt?.let { Contents.of(it) },
            initialMessages = history
        )

        conversation = eng.createConversation(configBuilder)
        Log.d(tag, "Conversation created. System prompt length: ${systemPrompt?.length ?: 0}. History size: ${history.size}")
    }

    private fun recreateConversation(session: AiChatSession? = null) {
        conversation?.close()
        createConversation(session)
    }

    private fun unload() {
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

    // Chat History
    override fun observeChatHistory(): Flow<List<AiChatSession>> {
        return aiChatDao.observeAllSessions().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveSession(session: AiChatSession) {
        withContext(Dispatchers.IO) {
            aiChatDao.insertSession(AiChatSessionEntity.fromDomain(session))
        }
    }

    override suspend fun getSession(sessionId: String): AiChatSession? {
        return withContext(Dispatchers.IO) {
            aiChatDao.getSessionById(sessionId)?.toDomain()
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            aiChatDao.deleteSession(sessionId)
        }
    }
}
