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
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.tool
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.monsteraltech.habitly.feature.aiassistant.data.source.DeviceMemoryProbe
import com.monsteraltech.habitly.feature.aiassistant.data.source.LocalModelManager
import com.monsteraltech.habitly.feature.aiassistant.data.source.ModelLoadWatchdog
import com.monsteraltech.habitly.feature.aiassistant.data.tools.RoutineProposalTools
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiChatDao
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiChatSessionEntity
import com.monsteraltech.habitly.feature.aiassistant.data.worker.ModelDownloadWorker
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AvailableAiModels
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.model.ModelCompatibility
import com.monsteraltech.habitly.feature.aiassistant.domain.model.compatibilityWith
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAssistantRepositoryImpl @Inject constructor(
    private val localModelManager: LocalModelManager,
    private val aiChatDao: AiChatDao,
    private val sharedPreferences: SharedPreferences,
    private val firebaseAuth: FirebaseAuth,
    private val deviceMemoryProbe: DeviceMemoryProbe,
    private val modelLoadWatchdog: ModelLoadWatchdog,
    @ApplicationContext private val context: Context
) : AiAssistantRepository {

    private val tag = "AiAssistantRepository"

    /** UID de la cuenta activa. Acota el historial local para que no se filtre entre cuentas. */
    private fun currentUserId(): String? = firebaseAuth.currentUser?.uid

    private val _selectedModel = MutableStateFlow(getSavedModel())
    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    private val _activeSession = MutableStateFlow<AiChatSession?>(null)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationHistoryKey: String? = null

    /**
     * Generación nativa en vuelo (la última lanzada). Cancelar la corrutina que colecta NO
     * detiene el motor, así que esta es la única fuente de verdad para saber si es seguro
     * cerrar/recrear una conversación o cerrar el engine.
     */
    @Volatile
    private var activeGeneration: NativeGeneration? = null

    /**
     * Serializa crear/cerrar engine y conversation: sin él, dos cargas simultáneas (o una
     * carga cruzada con un cambio de modelo) construyen dos engines y uno se pierde sin
     * `close()` — una fuga de GB de memoria nativa.
     */
    private val engineMutex = Mutex()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    init {
        checkModelStatus(_selectedModel.value)
        repoScope.launch {
            localModelManager.cleanupLegacyModels()
            localModelManager.cleanupOrphanedModels(AvailableAiModels.models)
        }
        workManager.cancelUniqueWork(ModelDownloadWorker.LEGACY_WORK_NAME)
        observeDownloadWork()
    }

    /** Returns saved model configuration or fallback based on device RAM. */
    private fun getSavedModel(): AiModelConfig {
        val savedId = sharedPreferences.getString("selected_model_id", null)
        AvailableAiModels.models.find { it.id == savedId }?.let { return it }
        return bestModelForDevice()
    }

    private fun bestModelForDevice(): AiModelConfig {
        val ram = deviceMemoryProbe.totalRamBytes()
        return AvailableAiModels.models
            .lastOrNull { it.compatibilityWith(ram).canUse }
            ?: AvailableAiModels.models.first()
    }

    override fun getAvailableModels(): List<AiModelConfig> = AvailableAiModels.models

    override fun getDeviceRamBytes(): Long = deviceMemoryProbe.totalRamBytes()

    override fun clearLoadFailures(modelId: String) {
        modelLoadWatchdog.reset(modelId)
        if (modelId == _selectedModel.value.id) checkModelStatus(_selectedModel.value)
    }

    override fun observeSelectedModel(): Flow<AiModelConfig> = _selectedModel.asStateFlow()

    override fun selectModel(modelId: String) {
        val config = AvailableAiModels.models.find { it.id == modelId } ?: return
        if (_selectedModel.value.id == config.id) return

        sharedPreferences.edit().putString("selected_model_id", config.id).apply()
        _selectedModel.value = config

        conversationHistoryKey = null
        // Cerrar un engine de GB es trabajo pesado: fuera del hilo principal (esto se llama
        // desde la UI) y bajo el mutex para no cruzarse con una carga en curso.
        repoScope.launch { engineMutex.withLock { unload() } }
        checkModelStatus(config)
    }

    override fun observeModelStatus(): Flow<ModelStatus> = _modelStatus.asStateFlow()

    /** Updates status for the specified model based on RAM compatibility, crash history, and file existence. */
    private fun checkModelStatus(config: AiModelConfig) {
        val compatibility = config.compatibilityWith(deviceMemoryProbe.totalRamBytes())
        if (compatibility == ModelCompatibility.Unsupported) {
            _modelStatus.value = ModelStatus.Unsupported
            return
        }
        if (modelLoadWatchdog.failedAttempts(config.id) >= MAX_LOAD_ATTEMPTS) {
            _modelStatus.value = ModelStatus.LoadCrashed
            return
        }
        val path = localModelManager.getModelPath(config)
        if (path != null) {
            _modelStatus.value = ModelStatus.Ready
        } else {
            _modelStatus.value = ModelStatus.NotDownloaded
        }
    }

    override suspend fun downloadModel(wifiOnly: Boolean) {
        val config = _selectedModel.value

        if (config.compatibilityWith(deviceMemoryProbe.totalRamBytes()) == ModelCompatibility.Unsupported) {
            Log.w(tag, "Download rejected: ${config.id} unsupported on this device")
            _modelStatus.value = ModelStatus.Unsupported
            return
        }

        _modelStatus.value = ModelStatus.Downloading(0f)

        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to config.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            ModelDownloadWorker.workNameFor(config.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Refleja el estado del worker de descarga en [_modelStatus]. Vive en el scope
     * de aplicación, así que sigue actualizando aunque el usuario cambie de pantalla.
     * Con [flatMapLatest] se observa siempre el work del modelo seleccionado: al cambiar
     * de modelo, la suscripción salta a su descarga (si la hay).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDownloadWork() {
        repoScope.launch {
            _selectedModel
                .flatMapLatest { model ->
                    workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workNameFor(model.id))
                }
                .collect { infos ->
                    val info = infos.lastOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> _modelStatus.value = ModelStatus.Downloading(0f)
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                            _modelStatus.value = ModelStatus.Downloading(progress)
                        }
                        WorkInfo.State.SUCCEEDED,
                        WorkInfo.State.CANCELLED -> {
                            checkModelStatus(_selectedModel.value)
                            workManager.pruneWork()
                        }
                        WorkInfo.State.FAILED -> {
                            val message = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                                ?: "Download error"
                            _modelStatus.value = ModelStatus.Error(message)
                            workManager.pruneWork()
                        }
                    }
                }
        }
    }

    override fun cancelDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.workNameFor(_selectedModel.value.id))
    }

    override suspend fun deleteModel(modelId: String) {
        val config = AvailableAiModels.models.find { it.id == modelId } ?: return
        workManager.cancelUniqueWork(ModelDownloadWorker.workNameFor(config.id))
        withContext(Dispatchers.IO) {
            if (config.id == _selectedModel.value.id) {
                engineMutex.withLock { unload() }
            }
            localModelManager.deleteModel(config)
            modelLoadWatchdog.reset(config.id)
            if (config.id == _selectedModel.value.id) {
                checkModelStatus(config)
            }
        }
    }

    override suspend fun getDownloadedModelIds(): Set<String> = withContext(Dispatchers.IO) {
        AvailableAiModels.models
            .filter { localModelManager.getModelPath(it) != null }
            .map { it.id }
            .toSet()
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
            engineMutex.withLock {
                recreateConversation(sessionNow)
            }
        }

        val conv = conversation
            ?: throw IllegalStateException("Modelo no cargado")

        conversationHistoryKey = null

        // Get the last USER message
        val prompt = sessionNow.messages
            .lastOrNull { it.role == MessageRole.User }
            ?.content ?: ""

        Log.d(tag, "Sending prompt to LiteRT-LM (${prompt.length} chars)")

        var emittedAny = false
        try {
            streamGeneration(conv, prompt).collect { chunk ->
                emittedAny = true
                emit(chunk)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retries once with truncated history if prefill fails before first token.
            if (emittedAny) throw e
            Log.w(tag, "Prefill failed; retrying with minimal history", e)
            val retryConv = engineMutex.withLock {
                recreateConversation(sessionNow, HISTORY_CHAR_BUDGET_RETRY)
                conversation
            } ?: throw e
            streamGeneration(retryConv, prompt).collect { chunk ->
                emit(chunk)
            }
        }
        conversationHistoryKey = sessionNow.id
    }.flowOn(Dispatchers.Default)

    /**
     * Bridges [Conversation.sendMessageAsync] callbacks to Flow with cancellation handling.
     */
    private fun streamGeneration(conv: Conversation, prompt: String): Flow<String> =
        callbackFlow {
            val generation = NativeGeneration(conv)
            activeGeneration = generation
            val callback = object : MessageCallback {
                override fun onMessage(message: Message) {
                    trySend(message.toString())
                }

                override fun onDone() {
                    generation.markFinished()
                    close()
                }

                override fun onError(throwable: Throwable) {
                    generation.markFinished()
                    close(throwable)
                }
            }
            try {
                conv.sendMessageAsync(prompt, callback)
            } catch (e: Exception) {
                generation.markFinished()
                throw e
            }
            awaitClose { generation.cancel() }
        }.buffer(Channel.UNLIMITED)

    /** Cancels native generation in flight and waits for engine confirmation. */
    private suspend fun awaitGenerationIdle() {
        val generation = activeGeneration ?: return
        withContext(NonCancellable) {
            generation.cancel()
            if (!generation.awaitFinished(GENERATION_STOP_TIMEOUT_MS)) {
                Log.w(tag, "Native generation stop unconfirmed; proceeding with close")
                generation.markFinished()
            }
        }
    }

    private suspend fun closeConversationSafely(conv: Conversation) {
        awaitGenerationIdle()
        runCatching { conv.close() }
    }

    override suspend fun extractRoutines(sourceText: String): String =
        withContext(Dispatchers.Default) {
            if (engine == null) loadModel(_activeSession.value)
            if (_selectedModel.value.supportsToolCalling) {
                try {
                    val result = toolExtraction(sourceText)
                    if (result.isNotEmpty()) return@withContext result
                    Log.w(tag, "Tool-calling produced empty result; falling back to JSON extraction")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "Tool extraction failed; falling back to JSON extraction", e)
                }
            }
            jsonExtraction(sourceText, ROUTINE_JSON_INSTRUCTION)
        }

    override suspend fun extractShopping(sourceText: String): String =
        withContext(Dispatchers.Default) {
            if (engine == null) loadModel(_activeSession.value)
            jsonExtraction(sourceText, SHOPPING_JSON_INSTRUCTION)
        }

    override suspend fun generateSessionTitle(userMessage: String, assistantReply: String): String =
        withContext(Dispatchers.Default) {
            val eng = engine ?: return@withContext ""
            prepareForEphemeralConversation()

            val titleConv = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = EXTRACTION_TEMPERATURE),
                    systemInstruction = Contents.of(TITLE_INSTRUCTION)
                )
            )
            try {
                val builder = StringBuilder()
                val source = "Usuario: ${userMessage.take(TITLE_SOURCE_MAX_CHARS)}\n" +
                    "Asistente: ${assistantReply.take(TITLE_SOURCE_MAX_CHARS)}"
                streamGeneration(titleConv, source).collect { chunk ->
                    builder.append(chunk)
                }
                sanitizeTitle(builder.toString())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "No se pudo generar el título de la sesión", e)
                ""
            } finally {
                closeConversationSafely(titleConv)
            }
        }

    override suspend fun summarizeConversation(sourceText: String): String =
        withContext(Dispatchers.Default) {
            if (sourceText.isBlank()) return@withContext ""
            if (engine == null) loadModel(_activeSession.value)
            val eng = engine ?: return@withContext ""
            prepareForEphemeralConversation()

            val summaryConv = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = EXTRACTION_TEMPERATURE),
                    systemInstruction = Contents.of(SUMMARY_INSTRUCTION)
                )
            )
            try {
                val builder = StringBuilder()
                streamGeneration(summaryConv, sourceText).collect { builder.append(it) }
                builder.toString().trim()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "No se pudo resumir la conversación", e)
                ""
            } finally {
                closeConversationSafely(summaryConv)
            }
        }

    /** Primera línea con contenido, sin comillas/markdown y acotada. */
    private fun sanitizeTitle(raw: String): String =
        raw.lineSequence()
            .map { it.trim().trim('"', '\'', '#', '*', '-', '.', ':').trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(MAX_SESSION_TITLE_CHARS)
            ?: ""

    /** Extractor por JSON (Fase 1): pide el JSON con [instruction] y lo devuelve tal cual. */
    private suspend fun jsonExtraction(sourceText: String, instruction: String): String {
        val eng = engine ?: return ""
        prepareForEphemeralConversation()

        val extractionConv = eng.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = EXTRACTION_TEMPERATURE),
                systemInstruction = Contents.of(instruction)
            )
        )
        return try {
            val builder = StringBuilder()
            streamGeneration(extractionConv, sourceText).collect { chunk ->
                builder.append(chunk)
            }
            builder.toString()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error en la extracción por JSON", e)
            ""
        } finally {
            closeConversationSafely(extractionConv)
        }
    }

    /**
     * Extractor por function-calling con constrained decoding (Fase 3): el modelo llama a
     * `addRoutine(title, frequency)` por cada rutina y el motor garantiza llamadas válidas. Se
     * recolectan por callback y se serializan al mismo JSON `{"routines":[...]}` que consume el
     * parser, así el resto del flujo (adjuntar @@RUTINA@@, tarjeta, recarga) no cambia.
     */
    @OptIn(ExperimentalApi::class)
    private suspend fun toolExtraction(sourceText: String): String {
        val eng = engine ?: return ""
        prepareForEphemeralConversation()

        val collected = mutableListOf<Pair<String, String>>()
        val toolSet = RoutineProposalTools { title, frequency -> collected += title to frequency }

        ExperimentalFlags.enableConversationConstrainedDecoding = true
        val extractionConv = eng.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = EXTRACTION_TEMPERATURE),
                systemInstruction = Contents.of(TOOL_EXTRACTION_INSTRUCTION),
                tools = listOf(tool(toolSet))
            )
        )
        try {
            // El texto emitido se ignora: las rutinas llegan por el callback de la tool.
            streamGeneration(extractionConv, sourceText).collect { }
        } finally {
            closeConversationSafely(extractionConv)
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
        return buildRoutinesJson(collected)
    }

    /** Cierra la conversación principal para no tener dos vivas por engine (se recrea al enviar). */
    private suspend fun prepareForEphemeralConversation() {
        engineMutex.withLock {
            conversation?.let { closeConversationSafely(it) }
            conversation = null
            conversationHistoryKey = null
        }
    }

    /** Serializa las rutinas recogidas al JSON `{"routines":[{"title":…,"frequency":…}]}`. */
    private fun buildRoutinesJson(pairs: List<Pair<String, String>>): String {
        if (pairs.isEmpty()) return ""
        val array = JsonArray()
        for ((title, frequency) in pairs) {
            array.add(JsonObject().apply {
                addProperty("title", title)
                addProperty("frequency", frequency)
            })
        }
        return JsonObject().apply { add("routines", array) }.toString()
    }

    override suspend fun resetSession() {
        withContext(Dispatchers.Default) {
            engineMutex.withLock {
                conversationHistoryKey = null
                recreateConversation(_activeSession.value)
            }
        }
    }

    override fun isModelLoaded(): Boolean = engine != null

    @OptIn(ExperimentalApi::class)
    private suspend fun loadModel(session: AiChatSession? = null) {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                if (engine != null) return@withLock
                loadModelLocked(session)
            }
        }
    }

    /** Carga real del engine. Llamar SIEMPRE con [engineMutex] cogido. */
    @OptIn(ExperimentalApi::class)
    private suspend fun loadModelLocked(session: AiChatSession? = null) {
        val config = _selectedModel.value

        // Puerta 1: el dispositivo no da. Ni se intenta: intentarlo es perder el proceso.
        if (config.compatibilityWith(deviceMemoryProbe.totalRamBytes()) == ModelCompatibility.Unsupported) {
            Log.w(tag, "Carga rechazada: ${config.id} no cabe en este dispositivo")
            _modelStatus.value = ModelStatus.Unsupported
            return
        }

        val failedAttempts = modelLoadWatchdog.onLoadStarting(config.id)
        if (failedAttempts >= MAX_LOAD_ATTEMPTS) {
            Log.w(tag, "Load aborted after $failedAttempts failed attempts. ${modelLoadWatchdog.lastExitDiagnosis()}")
            _modelStatus.value = ModelStatus.LoadCrashed
            return
        }

        val degraded = failedAttempts > 0
        if (degraded) {
            Log.w(tag, "Degraded retry for ${config.id} (CPU, reduced context)")
        }

        try {
            val modelPath = localModelManager.getModelPath(config)
                ?: throw IllegalStateException("Modelo no descargado")

            val maxTokens = if (degraded) config.maxTokens / 2 else config.maxTokens

            engine = if (!config.supportsGpu || degraded) {
                buildEngine(config, modelPath, Backend.CPU(), speculative = false, maxTokens = maxTokens)
            } else {
                try {
                    buildEngine(config, modelPath, Backend.GPU(), config.supportsSpeculativeDecoding, maxTokens)
                } catch (e: Exception) {
                    Log.w(tag, "GPU backend unavailable; falling back to CPU", e)
                    buildEngine(config, modelPath, Backend.CPU(), speculative = false, maxTokens = maxTokens)
                }
            }

            warmUp(engine!!)
            createConversation(session)
            modelLoadWatchdog.onLoadSucceeded(config.id)
            Log.d(tag, "LiteRT-LM engine loaded from: $modelPath")
            _modelStatus.value = ModelStatus.Ready
        } catch (e: kotlinx.coroutines.CancellationException) {
            modelLoadWatchdog.onLoadFailedGracefully(config.id)
            throw e
        } catch (e: Exception) {
            modelLoadWatchdog.onLoadFailedGracefully(config.id)
            Log.e(tag, "Error loading LiteRT-LM model", e)
            _modelStatus.value = ModelStatus.Error(
                "Error loading model: ${e.message}"
            )
        }
    }

    /**
     * Initializes engine with specified backend and speculative decoding flags.
     */
    @OptIn(ExperimentalApi::class)
    private fun buildEngine(
        config: AiModelConfig,
        modelPath: String,
        backend: Backend,
        speculative: Boolean,
        maxTokens: Int = config.maxTokens
    ): Engine {
        ExperimentalFlags.enableSpeculativeDecoding = speculative
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxTokens,
            cacheDir = context.cacheDir.path
        )
        val eng = Engine(engineConfig)
        try {
            eng.initialize()
        } catch (e: Exception) {
            eng.close()
            throw e
        }
        return eng
    }

    /** Warmup single-token pass to compile GPU/JIT kernels. */
    private suspend fun warmUp(eng: Engine) {
        try {
            withTimeoutOrNull(WARMUP_TIMEOUT_MS) {
                val conv = eng.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)
                    )
                )
                try {
                    streamGeneration(conv, "Hola").take(1).collect { }
                } finally {
                    closeConversationSafely(conv)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Warmup failed (non-critical)", e)
        }
    }

    private fun createConversation(
        session: AiChatSession? = null,
        historyCharBudget: Int = HISTORY_CHAR_BUDGET
    ) {
        val eng = engine ?: return

        val basePrompt = session?.systemPrompt?.takeIf { it.isNotBlank() }
        val summary = session?.contextSummary?.takeIf { it.isNotBlank() }
        val systemInstruction = listOfNotNull(
            basePrompt,
            summary?.let { "[Summary of previous conversation]\n$it" }
        ).joinToString("\n\n").takeIf { it.isNotBlank() }
        val history = buildHistory(session, historyCharBudget)

        val configBuilder = ConversationConfig(
            samplerConfig = SamplerConfig(
                topK = 64,
                topP = 0.95,
                temperature = _selectedModel.value.defaultTemperature
            ),
            systemInstruction = systemInstruction?.let { Contents.of(it) },
            initialMessages = history
        )

        conversation = eng.createConversation(configBuilder)
        Log.d(tag, "Conversation created. System prompt length: ${systemInstruction?.length ?: 0}. History size: ${history.size}")
    }

    /** Truncates conversation history to fit KV cache budget. */
    private fun buildHistory(session: AiChatSession?, charBudget: Int): List<Message> {
        val messages = session?.messages.orEmpty()
            .drop(session?.summarizedUpTo ?: 0)
            .filter { it.content.isNotBlank() }
            .dropLastWhile { it.role == MessageRole.User }

        val trimmed = ArrayDeque<Message>()
        var used = 0
        for (msg in messages.asReversed()) {
            val content = msg.content.take(HISTORY_MESSAGE_MAX_CHARS)
            if (used + content.length > charBudget && trimmed.isNotEmpty()) break
            val mapped = when (msg.role) {
                MessageRole.User -> Message.user(content)
                MessageRole.Assistant -> Message.model(content)
                else -> null
            } ?: continue
            trimmed.addFirst(mapped)
            used += content.length
        }
        return trimmed.toList()
    }

    private suspend fun recreateConversation(
        session: AiChatSession? = null,
        historyCharBudget: Int = HISTORY_CHAR_BUDGET
    ) {
        conversation?.let { closeConversationSafely(it) }
        conversation = null
        createConversation(session, historyCharBudget)
    }

    private suspend fun unload() {
        awaitGenerationIdle()
        conversation?.let { runCatching { it.close() } }
        engine?.let { runCatching { it.close() } }
        conversation = null
        engine = null
    }

    override fun observeChatHistory(): Flow<List<AiChatSession>> = flow {
        val userId = currentUserId()
        if (userId == null) {
            emit(emptyList())
        } else {
            emitAll(
                aiChatDao.observeSessionsForUser(userId).map { list ->
                    list.map { it.toDomain() }
                }
            )
        }
    }

    override suspend fun saveSession(session: AiChatSession) {
        val userId = currentUserId()
        if (userId == null) {
            Log.w(tag, "saveSession called without active user; ignoring")
            return
        }
        withContext(Dispatchers.IO) {
            aiChatDao.insertSession(AiChatSessionEntity.fromDomain(session, userId))
        }
    }

    override suspend fun getSession(sessionId: String): AiChatSession? {
        return withContext(Dispatchers.IO) {
            val userId = currentUserId() ?: return@withContext null
            aiChatDao.getSessionByIdForUser(sessionId, userId)?.toDomain()
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            val userId = currentUserId() ?: return@withContext
            aiChatDao.deleteSessionForUser(sessionId, userId)
        }
    }

    /** Manages active native generation lifecycle and cancellation signaling. */
    private class NativeGeneration(private val conversation: Conversation) {
        private val finished = CompletableDeferred<Unit>()

        fun markFinished() {
            finished.complete(Unit)
        }

        fun cancel() {
            if (!finished.isCompleted) {
                runCatching { conversation.cancelProcess() }
            }
        }

        suspend fun awaitFinished(timeoutMs: Long): Boolean =
            withTimeoutOrNull(timeoutMs) { finished.await() } != null
    }

    private companion object {
        const val MAX_LOAD_ATTEMPTS = 2
        const val EXTRACTION_TEMPERATURE = 0.15
        const val HISTORY_CHAR_BUDGET = 6000
        const val HISTORY_CHAR_BUDGET_RETRY = 1500
        const val HISTORY_MESSAGE_MAX_CHARS = 2000
        const val WARMUP_TIMEOUT_MS = 8000L
        const val GENERATION_STOP_TIMEOUT_MS = 10_000L
        const val TITLE_SOURCE_MAX_CHARS = 300
        const val MAX_SESSION_TITLE_CHARS = 40

        /** System prompt del generador de títulos de sesión. */
        const val TITLE_INSTRUCTION =
            "Resume el tema de la conversación en un título muy corto, de 3 a 5 palabras, en " +
                "español. Devuelve SOLO el título: sin comillas, sin punto final y sin texto adicional."

        /** System prompt del resumen de compactación de contexto. */
        const val SUMMARY_INSTRUCTION =
            "Resume la conversación en como máximo 10 viñetas y 900 caracteres, en español: qué " +
                "pidió el usuario, decisiones tomadas, listas o rutinas ya creadas, y preferencias " +
                "o datos personales mencionados. Empieza cada viñeta con \"- \". No añadas saludos " +
                "ni texto fuera de las viñetas."

        /** System prompt del extractor de rutinas por function-calling (Fase 3). */
        const val TOOL_EXTRACTION_INSTRUCTION =
            "Eres un extractor. Del texto del usuario, registra cada rutina que se propone CREAR " +
                "llamando a la herramienta addRoutine(title, frequency), una vez por rutina. Si no " +
                "se propone crear ninguna rutina, no llames a la herramienta. No escribas texto."

        /** System prompt del extractor de rutinas por JSON (fallback del function-calling). */
        const val ROUTINE_JSON_INSTRUCTION =
            "Eres un extractor de datos. A partir del texto que te da el usuario, identifica las " +
                "rutinas o hábitos que se proponen CREAR. Devuelve EXCLUSIVAMENTE un JSON en una " +
                "sola línea con esta forma: " +
                "{\"routines\":[{\"title\":\"...\",\"frequency\":\"diaria|semanal|cada_n_dias\"}]}. " +
                "El \"title\" debe ser corto y empezar por verbo. Si el texto no propone crear " +
                "ninguna rutina, devuelve {\"routines\":[]}. No añadas texto fuera del JSON."

        /** System prompt del extractor de la lista de la compra (por JSON). */
        const val SHOPPING_JSON_INSTRUCTION =
            "Eres un extractor de datos. A partir del texto del usuario, identifica los productos " +
                "que se recomienda COMPRAR. Devuelve EXCLUSIVAMENTE un JSON en una sola línea: " +
                "{\"shopping_list\":[{\"name\":\"...\",\"quantity\":1,\"unit\":\"unidad\",\"category\":\"...\"}]}. " +
                "'name' corto y en singular; 'quantity' entero; 'unit' una de: unidad, kg, g, L, ml, " +
                "docena, paquete; 'category' una de: Frutas y Verduras, Carnes y Pescados, Lacteos y " +
                "Huevos, Panaderia y Cereales, Despensa y Conservas, Limpieza y Hogar, Bebidas. Si no " +
                "se recomienda comprar nada, devuelve {\"shopping_list\":[]}. No añadas texto fuera del JSON."
    }
}
