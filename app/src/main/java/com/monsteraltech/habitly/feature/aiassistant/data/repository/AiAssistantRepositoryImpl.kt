package com.monsteraltech.habitly.feature.aiassistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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

    /** Active account uid. Scopes the local history so it cannot leak between accounts. */
    private fun currentUserId(): String? = firebaseAuth.currentUser?.uid

    private val _selectedModel = MutableStateFlow(getSavedModel())
    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    private val _activeSession = MutableStateFlow<AiChatSession?>(null)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationHistoryKey: String? = null

    /**
     * The in-flight native generation (the last one launched). Cancelling the collecting coroutine
     * does **not** stop the engine, so this is the only source of truth for whether it is safe to
     * close or recreate a conversation, or close the engine.
     */
    @Volatile
    private var activeGeneration: NativeGeneration? = null

    /**
     * Serialises creating and closing the engine and conversation. Without it, two simultaneous
     * loads — or a load crossed with a model change — build two engines and one is lost without
     * `close()`, leaking gigabytes of native memory.
     */
    private val engineMutex = Mutex()

    // Application scope (lives as long as the singleton), independent of ViewModel lifecycles.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    init {
        checkModelStatus(_selectedModel.value)
        // Cleans up legacy and orphaned models, and observes downloads off the main thread.
        repoScope.launch {
            localModelManager.cleanupLegacyModels()
            localModelManager.cleanupOrphanedModels(AvailableAiModels.models)
        }
        // The old work name was shared across models; cancelled so no orphaned download is left
        // running with nobody observing it.
        workManager.cancelUniqueWork(ModelDownloadWorker.LEGACY_WORK_NAME)
        observeDownloadWork()
    }

    /**
     * The stored model, or the best one the device can handle when there is none. Without that
     * second case a 4 GB phone would start the app on a model it cannot even download. An explicit
     * user choice is respected even when it is too big — that is what the on-screen warning is for.
     */
    private fun getSavedModel(): AiModelConfig {
        val savedId = sharedPreferences.getString("selected_model_id", null)
        AvailableAiModels.models.find { it.id == savedId }?.let { return it }
        return bestModelForDevice()
    }

    /** The most capable model the device supports; the lightest one if none fit. */
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

        sharedPreferences.edit { putString("selected_model_id", config.id) }
        _selectedModel.value = config

        conversationHistoryKey = null
        // Closing a multi-gigabyte engine is heavy work: off the main thread (this is called from
        // the UI) and under the mutex so it cannot cross a load in progress.
        repoScope.launch { engineMutex.withLock { unload() } }
        checkModelStatus(config)
    }

    override fun observeModelStatus(): Flow<ModelStatus> = _modelStatus.asStateFlow()

    /**
     * Status of the selected model. Order matters: first whatever makes it impossible to even try
     * (does not fit the device, or already killed the process), and only then whether it is on
     * disk. Downloading 700 MB or 2.6 GB for something that will not start is the worst outcome.
     */
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

        // Hard gate: the UI does not reach here under normal conditions (the button is disabled),
        // but this is the last line before spending gigabytes of the user's data plan.
        if (config.compatibilityWith(deviceMemoryProbe.totalRamBytes()) == ModelCompatibility.Unsupported) {
            Log.w(tag, "Descarga rechazada: ${config.id} no cabe en este dispositivo")
            _modelStatus.value = ModelStatus.Unsupported
            return
        }

        _modelStatus.value = ModelStatus.Downloading(0f)

        // UNMETERED = Wi-Fi only (or other unmetered networks); CONNECTED = mobile data too. The
        // user picks this in the preceding dialog, since the model weighs gigabytes.
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to config.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            // Backoff for the worker's Result.retry() calls (transient network errors).
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Unique name **per model** plus KEEP: re-requesting the same model does not restart its
        // download, and requesting a different one is no longer dropped silently — the name used
        // to be shared, so KEEP swallowed the second request.
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.workNameFor(config.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Mirrors the download worker's state into [_modelStatus]. Lives in the application scope, so
     * it keeps updating even when the user leaves the screen. [flatMapLatest] always observes the
     * selected model's work, so changing model moves the subscription to its download, if any.
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
                            // Without pruning, this terminal state would be re-emitted on every launch.
                            workManager.pruneWork()
                        }
                        WorkInfo.State.FAILED -> {
                            val message = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                                ?: "Error de descarga"
                            _modelStatus.value = ModelStatus.Error(message)
                            workManager.pruneWork()
                        }
                    }
                }
        }
    }

    override fun cancelDownload() {
        // The worker keeps the .tmp file on cancellation: a new download resumes where it stopped.
        workManager.cancelUniqueWork(ModelDownloadWorker.workNameFor(_selectedModel.value.id))
    }

    override suspend fun deleteModel(modelId: String) {
        val config = AvailableAiModels.models.find { it.id == modelId } ?: return
        // Any download in progress is stopped before its files are deleted.
        workManager.cancelUniqueWork(ModelDownloadWorker.workNameFor(config.id))
        withContext(Dispatchers.IO) {
            if (config.id == _selectedModel.value.id) {
                engineMutex.withLock { unload() }
            }
            localModelManager.deleteModel(config)
            // Delete and re-download is the natural move after a load failure, so the new file must
            // not inherit the previous one's ban — it may simply have been corrupt.
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

        // Pessimistic marking: only a clean finish, at the foot of this block, revalidates the key.
        // If generation ends badly (stop, new chat, mid-stream error) the native history no longer
        // matches the session the user sees, and the next send must recreate the conversation from
        // the persisted session instead of reusing it.
        conversationHistoryKey = null

        // Get the last USER message
        val prompt = sessionNow.messages
            .lastOrNull { it.role == MessageRole.User }
            ?.content ?: ""

        // Length only: the message content belongs to the user and must not reach logcat.
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
            // Failure before the first token: typically history + prompt exceeding maxNumTokens on
            // a long session. Retried **once** with the history trimmed to the minimum. A failure
            // mid-answer is not retried, since that would duplicate text already on screen.
            if (emittedAny) throw e
            Log.w(tag, "Fallo en prefill; se reintenta con historia mínima", e)
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
     * Hand-rolled bridge over [Conversation.sendMessageAsync] with [MessageCallback], instead of
     * the Flow that ships with litertlm 0.14.0.
     *
     * The library's Flow has an **empty** `awaitClose`: cancelling the collection (stop, new chat,
     * model change) tells the engine nothing, and the orphaned decode stays alive until it runs
     * out — closing or recreating the conversation meanwhile locks the engine up. Here
     * cancellation triggers [NativeGeneration.cancel] → `cancelProcess()`, and onDone/onError
     * record the real death so [awaitGenerationIdle] can wait for it.
     *
     * Unlimited buffer: with the default of 64, the native callback's `trySend` would drop tokens
     * whenever the collector fell momentarily behind.
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

    /**
     * Cancels the in-flight native generation if it is still alive and waits for the engine to
     * confirm its death — onDone/onError arrive after `cancelProcess()` too. **Mandatory** before
     * closing or recreating a conversation, or closing the engine.
     *
     * Runs in [NonCancellable] because it almost always executes during the cleanup of an already
     * cancelled job. The wait is bounded so an engine that never confirms cannot chain blockages.
     */
    private suspend fun awaitGenerationIdle() {
        val generation = activeGeneration ?: return
        withContext(NonCancellable) {
            generation.cancel()
            if (!generation.awaitFinished(GENERATION_STOP_TIMEOUT_MS)) {
                Log.w(tag, "La generación nativa no confirmó su parada; se continúa con el cierre")
                // Do not wait on it again: the conversation it belongs to is being closed now.
                generation.markFinished()
            }
        }
    }

    /** Closes a conversation, first waiting for the engine to release its in-flight generation. */
    private suspend fun closeConversationSafely(conv: Conversation) {
        awaitGenerationIdle()
        runCatching { conv.close() }
    }

    override suspend fun extractRoutines(sourceText: String): String =
        withContext(Dispatchers.Default) {
            // Extraction can be requested without going through the chat (text shared from another
            // app), and the engine is not loaded yet in that case.
            if (engine == null) loadModel(_activeSession.value)
            // Function calling with constrained decoding is preferred, since the engine guarantees
            // the structure. If the model does not support it or it fails at runtime, fall back to
            // the JSON extractor.
            if (_selectedModel.value.supportsToolCalling) {
                try {
                    val result = toolExtraction(sourceText)
                    // Empty is suspicious too: the most common failure mode in the field is not an
                    // exception, it is the model answering in prose without calling the tool. The
                    // intent gate already said the user wants routines, so an empty result earns
                    // the JSON retry — and if there really are no routines, the JSON extractor
                    // returns empty as well and no card appears.
                    if (result.isNotEmpty()) return@withContext result
                    Log.w(tag, "Function-calling sin llamadas a la tool; se reintenta por JSON")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "Extracción de rutinas por function-calling falló; se reintenta por JSON", e)
                }
            }
            jsonExtraction(sourceText, ROUTINE_JSON_INSTRUCTION)
        }

    override suspend fun extractShopping(sourceText: String): String =
        withContext(Dispatchers.Default) {
            // As in extractRoutines: shared text arrives without a prior chat.
            if (engine == null) loadModel(_activeSession.value)
            // A shopping item has 4 fields, and tool calling with 4 arguments proved unreliable on
            // E2B in the field, so this path always uses the JSON extractor.
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
            // Compaction can be requested on a reloaded chat while the engine is still unloaded.
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

    /** First non-empty line, stripped of quotes and markdown, and length-capped. */
    private fun sanitizeTitle(raw: String): String =
        raw.lineSequence()
            .map { it.trim().trim('"', '\'', '#', '*', '-', '.', ':').trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(MAX_SESSION_TITLE_CHARS)
            ?: ""

    /** JSON extractor: asks for the JSON with [instruction] and returns it verbatim. */
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
     * Function-calling extractor with constrained decoding: the model calls
     * `addRoutine(title, frequency)` once per routine and the engine guarantees valid calls. They
     * are collected by callback and serialised into the same `{"routines":[...]}` JSON the parser
     * consumes, so the rest of the flow is unchanged.
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
            // The emitted text is ignored: the routines arrive through the tool callback.
            streamGeneration(extractionConv, sourceText).collect { }
        } finally {
            closeConversationSafely(extractionConv)
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
        return buildRoutinesJson(collected)
    }

    /** Closes the main conversation so only one lives per engine; it is recreated on the next send. */
    private suspend fun prepareForEphemeralConversation() {
        engineMutex.withLock {
            conversation?.let { closeConversationSafely(it) }
            conversation = null
            conversationHistoryKey = null
        }
    }

    /** Serialises the collected routines into `{"routines":[{"title":…,"frequency":…}]}`. */
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
                // Another coroutine may have loaded it while we waited for the mutex.
                if (engine != null) return@withLock
                loadModelLocked(session)
            }
        }
    }

    /** The real engine load. **Always** call with [engineMutex] held. */
    @OptIn(ExperimentalApi::class)
    private suspend fun loadModelLocked(session: AiChatSession? = null) {
        val config = _selectedModel.value

        // Gate 1: the device cannot take it. Not even attempted — attempting means losing the process.
        if (config.compatibilityWith(deviceMemoryProbe.totalRamBytes()) == ModelCompatibility.Unsupported) {
            Log.w(tag, "Carga rechazada: ${config.id} no cabe en este dispositivo")
            _modelStatus.value = ModelStatus.Unsupported
            return
        }

        // Gate 2: it already took the process down before. `onLoadStarting` writes the marker to
        // disk **before** touching the engine, so if this call never returns, the next one knows.
        val failedAttempts = modelLoadWatchdog.onLoadStarting(config.id)
        if (failedAttempts >= MAX_LOAD_ATTEMPTS) {
            Log.w(tag, "Carga abandonada tras $failedAttempts intentos fallidos. ${modelLoadWatchdog.lastExitDiagnosis()}")
            _modelStatus.value = ModelStatus.LoadCrashed
            return
        }

        // One previous failure (not two) lowers expectations: CPU and half the KV cache. If the
        // first attempt died on memory, repeating it identically only repeats the kill.
        val degraded = failedAttempts > 0
        if (degraded) {
            Log.w(tag, "Reintento degradado de ${config.id} (CPU, contexto reducido)")
        }

        try {
            val modelPath = localModelManager.getModelPath(config)
                ?: throw IllegalStateException("Modelo no descargado")

            val maxTokens = if (degraded) config.maxTokens / 2 else config.maxTokens

            engine = if (!config.supportsGpu || degraded) {
                // Some models have graphs the GPU delegates do not support (LFM2.5 and its hybrid
                // convolution). Try-and-catch is **not** an option there: a delegate choking on a
                // graph it does not understand can go down with SIGSEGV, which no catch will see.
                buildEngine(config, modelPath, Backend.CPU(), speculative = false, maxTokens = maxTokens)
            } else {
                // GPU is far faster and the only place MTP (speculative decoding) pays off; if it
                // is unavailable, fall back to CPU and switch MTP off.
                try {
                    buildEngine(config, modelPath, Backend.GPU(), config.supportsSpeculativeDecoding, maxTokens)
                } catch (e: Exception) {
                    Log.w(tag, "Backend GPU no disponible, se usa CPU", e)
                    buildEngine(config, modelPath, Backend.CPU(), speculative = false, maxTokens = maxTokens)
                }
            }

            warmUp(engine!!)
            createConversation(session)
            // The marker is cleared only here: the engine exists and produced its warmup token.
            modelLoadWatchdog.onLoadSucceeded(config.id)
            Log.d(tag, "LiteRT-LM engine loaded from: $modelPath")
            _modelStatus.value = ModelStatus.Ready
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancelling the loading job (new chat, stop) is not a model error: it propagates
            // without overwriting the state with a false Error. It is not a silent death either —
            // the process is alive, so the marker is withdrawn.
            modelLoadWatchdog.onLoadFailedGracefully(config.id)
            throw e
        } catch (e: Exception) {
            // It failed, but with an exception: we are alive and this is not the case the watchdog
            // guards. The marker is cleared so it does not blame a kill that never happened.
            modelLoadWatchdog.onLoadFailedGracefully(config.id)
            Log.e(tag, "Error loading LiteRT-LM model", e)
            _modelStatus.value = ModelStatus.Error(
                "Error al cargar el modelo: ${e.message}"
            )
        }
    }

    /**
     * Builds and initialises the engine on a specific backend. MTP (speculative decoding) is a
     * global LiteRT-LM flag, enabled only when [speculative] holds (GPU plus a model with a
     * drafter). If initialisation fails the engine is closed before propagating, so a retry is
     * possible.
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

    /**
     * Generates a throwaway token after loading the engine, to force kernel compilation (JIT/GPU)
     * before the first real message. Timeout-bounded and failure-proof: it never blocks the load.
     */
    private suspend fun warmUp(eng: Engine) {
        try {
            withTimeoutOrNull(WARMUP_TIMEOUT_MS) {
                val conv = eng.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)
                    )
                )
                try {
                    // take(1) cancels the rest, and with the hand-rolled bridge that cancellation
                    // actually **stops** the native decode. Previously it kept generating and the
                    // close blocked until the whole greeting had been answered.
                    streamGeneration(conv, "Hola").take(1).collect { }
                } finally {
                    closeConversationSafely(conv)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Warmup falló (no crítico)", e)
        }
    }

    private fun createConversation(
        session: AiChatSession? = null,
        historyCharBudget: Int = HISTORY_CHAR_BUDGET
    ) {
        val eng = engine ?: return

        // System prompt plus, if the conversation was compacted, the summary of the older part, so
        // the model keeps the thread even though its history starts later.
        val basePrompt = session?.systemPrompt?.takeIf { it.isNotBlank() }
        val summary = session?.contextSummary?.takeIf { it.isNotBlank() }
        val systemInstruction = listOfNotNull(
            basePrompt,
            summary?.let { "[Resumen de la conversación previa]\n$it" }
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

    /**
     * Bounded history for `initialMessages`. The KV cache ([AiModelConfig.maxTokens]) has to cover
     * system prompt + history + prompt + answer, and an uncapped long session eventually fails at
     * prefill.
     *
     * Trimmed from the end (recent is what matters), capped per message, no blank placeholders,
     * and **without the last user turn**: that one is sent as the prompt in `sendMessageAsync`, and
     * a plain `dropLast(1)` let it in twice — once in the history and once as the prompt.
     */
    private fun buildHistory(session: AiChatSession?, charBudget: Int): List<Message> {
        // Messages already covered by the summary are skipped; they ride in the system prompt.
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
        // A single await covers both conversation and engine: the in-flight generation is the same.
        awaitGenerationIdle()
        conversation?.let { runCatching { it.close() } }
        engine?.let { runCatching { it.close() } }
        conversation = null
        engine = null
    }

    // Chat history.
    // The uid is read at the start of each collection or operation rather than captured once, so
    // after an account switch a fresh subscription already observes the right history.
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
            // With no active account the session cannot be attributed, so it is not persisted —
            // that would be invisible orphaned history. Should not happen from the assistant screen.
            Log.w(tag, "saveSession sin usuario activo; la sesión no se guarda")
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

    /**
     * One in-flight native generation. [cancel] signals the real stop (`cancelProcess()`, which
     * does **not** wait) and [awaitFinished] waits for the engine's confirmation: onDone/onError
     * fire when a generation dies by cancellation too.
     *
     * Without this piece, closing or recreating the conversation after a stop runs underneath a
     * live inference and locks the engine until the orphaned decode exhausts itself.
     */
    private class NativeGeneration(private val conversation: Conversation) {
        private val finished = CompletableDeferred<Unit>()

        fun markFinished() {
            finished.complete(Unit)
        }

        fun cancel() {
            if (!finished.isCompleted) {
                // cancelProcess throws if the conversation is already closed; benign here.
                runCatching { conversation.cancelProcess() }
            }
        }

        suspend fun awaitFinished(timeoutMs: Long): Boolean =
            withTimeoutOrNull(timeoutMs) { finished.await() } != null
    }

    private companion object {
        /**
         * How many load attempts may die without returning before giving up. Two allows one
         * degraded retry (CPU plus reduced context) in case the first fell to a transient memory
         * spike, and stops before it turns into a loop of process kills.
         */
        const val MAX_LOAD_ATTEMPTS = 2

        /** Low temperature for the extraction turn: format fidelity over creativity. */
        const val EXTRACTION_TEMPERATURE = 0.15

        /** Character budget for the history when rebuilding a conversation (~1,700 tokens): leaves
         *  room in the KV cache for the system prompt, the prompt and the answer. */
        const val HISTORY_CHAR_BUDGET = 6000

        /** Minimum budget for the retry after a prefill failure (context nearly full). */
        const val HISTORY_CHAR_BUDGET_RETRY = 1500

        /** Per-message cap inside the rebuilt history. */
        const val HISTORY_MESSAGE_MAX_CHARS = 2000

        /** Warmup time cap, so a stuck engine cannot block the load. */
        const val WARMUP_TIMEOUT_MS = 8000L

        /** Maximum wait for the engine to confirm (onDone/onError) the death of a cancelled
         *  generation before closing its conversation. Usually about one token; the margin covers
         *  long prefills, and it runs off the main thread. */
        const val GENERATION_STOP_TIMEOUT_MS = 10_000L

        /** Characters of each message handed to the title generator. */
        const val TITLE_SOURCE_MAX_CHARS = 300

        /** Session title cap (the history drawer is narrow). */
        const val MAX_SESSION_TITLE_CHARS = 40

        /** System prompt of the session title generator. */
        const val TITLE_INSTRUCTION =
            "Resume el tema de la conversación en un título muy corto, de 3 a 5 palabras, en " +
                "español. Devuelve SOLO el título: sin comillas, sin punto final y sin texto adicional."

        /** System prompt of the context-compaction summary. */
        const val SUMMARY_INSTRUCTION =
            "Resume la conversación en como máximo 10 viñetas y 900 caracteres, en español: qué " +
                "pidió el usuario, decisiones tomadas, listas o rutinas ya creadas, y preferencias " +
                "o datos personales mencionados. Empieza cada viñeta con \"- \". No añadas saludos " +
                "ni texto fuera de las viñetas."

        /** System prompt of the function-calling routine extractor. */
        const val TOOL_EXTRACTION_INSTRUCTION =
            "Eres un extractor. Del texto del usuario, registra cada rutina que se propone CREAR " +
                "llamando a la herramienta addRoutine(title, frequency), una vez por rutina. Si no " +
                "se propone crear ninguna rutina, no llames a la herramienta. No escribas texto."

        /** System prompt of the JSON routine extractor (fallback for function calling). */
        const val ROUTINE_JSON_INSTRUCTION =
            "Eres un extractor de datos. A partir del texto que te da el usuario, identifica las " +
                "rutinas o hábitos que se proponen CREAR. Devuelve EXCLUSIVAMENTE un JSON en una " +
                "sola línea con esta forma: " +
                "{\"routines\":[{\"title\":\"...\",\"frequency\":\"diaria|semanal|cada_n_dias\"}]}. " +
                "El \"title\" debe ser corto y empezar por verbo. Si el texto no propone crear " +
                "ninguna rutina, devuelve {\"routines\":[]}. No añadas texto fuera del JSON."

        /** System prompt of the shopping list extractor (JSON). */
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
