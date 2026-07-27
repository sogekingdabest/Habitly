package com.monsteraltech.habitly.feature.aiassistant.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.BuildConfig
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.FollowUpTarget
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.model.ModelCompatibility
import com.monsteraltech.habitly.feature.aiassistant.domain.model.compatibilityWith
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiItemsToShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.EstimateContextUsageUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetAiContextUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ReportAiMessageUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.RoutineCreationIntentUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ShoppingCreationIntentUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.util.AiStructuredBlocks
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val repository: AiAssistantRepository,
    private val getAiContextUseCase: GetAiContextUseCase,
    private val getContextualQuickPromptsUseCase: GetContextualQuickPromptsUseCase,
    private val parseAiShoppingListUseCase: ParseAiShoppingListUseCase,
    private val addAiItemsToShoppingListUseCase: AddAiItemsToShoppingListUseCase,
    private val parseAiRoutinesUseCase: ParseAiRoutinesUseCase,
    private val addAiRoutinesUseCase: AddAiRoutinesUseCase,
    private val routineCreationIntentUseCase: RoutineCreationIntentUseCase,
    private val shoppingCreationIntentUseCase: ShoppingCreationIntentUseCase,
    private val estimateContextUsageUseCase: EstimateContextUsageUseCase,
    private val reportAiMessageUseCase: ReportAiMessageUseCase
) : ViewModel() {

    private val tag = "AiAssistantViewModel"

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private var chatOperationJob: Job? = null

    /** Última elección de red del diálogo de descarga (la reutiliza "reintentar"). */
    private var lastDownloadWifiOnly = false

    init {
        _uiState.update {
            it.copy(
                availableModels = repository.getAvailableModels(),
                deviceRamBytes = repository.getDeviceRamBytes()
            )
        }

        refreshQuickPrompts()

        viewModelScope.launch {
            repository.observeSelectedModel().collectLatest { model ->
                _uiState.update { it.copy(selectedModel = model) }
                // Assign modelId to active session if new
                if (_uiState.value.chatSession.messages.isEmpty()) {
                    _uiState.update { state ->
                        state.copy(chatSession = state.chatSession.copy(modelId = model.id))
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeModelStatus().collectLatest { status ->
                _uiState.update { it.copy(modelStatus = status) }
                refreshDownloadedModels()
            }
        }

        viewModelScope.launch {
            repository.observeChatHistory().collectLatest { history ->
                _uiState.update { it.copy(chatHistory = history) }
            }
        }
    }

    /**
     * Recalcula los chips de sugerencia con el estado actual de la casa. Se hace en su
     * propio job porque lee lista y rutinas: no debe retrasar la carga del chat.
     */
    private fun refreshQuickPrompts() {
        viewModelScope.launch {
            val prompts = getContextualQuickPromptsUseCase()
            _uiState.update { it.copy(quickPrompts = prompts) }
        }
    }

    /**
     * Serializa las operaciones que tocan el engine (enviar, parar, nuevo chat, cargar,
     * cambiar de modelo): cada una CANCELA Y ESPERA a la anterior antes de empezar, así no
     * hay dos operaciones de chat pisándose. OJO: esto solo serializa corrutinas; la parada
     * REAL de la inferencia nativa vive en el repositorio (cancelar el stream dispara
     * `cancelProcess()`, y todo close/recreate espera la confirmación del motor). Sin esa
     * pieza, el join termina al morir la corrutina mientras el decode nativo sigue vivo.
     */
    private fun launchChatOperation(block: suspend CoroutineScope.() -> Unit) {
        val previous = chatOperationJob
        chatOperationJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            block()
        }
    }

    fun onSelectModel(modelId: String) {
        val model = _uiState.value.availableModels.find { it.id == modelId } ?: return
        if (!model.compatibilityWith(_uiState.value.deviceRamBytes).canUse) {
            _uiState.update { it.copy(errorRes = R.string.ai_model_unsupported_error) }
            return
        }
        launchChatOperation {
            repository.selectModel(modelId)
        }
    }

    fun onInputChange(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun onSendMessage() {
        val input = _uiState.value.currentInput.trim()
        if (input.isBlank() || _uiState.value.isGenerating) return

        val previousAssistantProse = _uiState.value.chatSession.messages
            .filter { it.role is MessageRole.Assistant }
            .takeLast(FOLLOW_UP_SOURCE_MESSAGES)
            .joinToString("\n\n") { AiStructuredBlocks.stripFromDisplay(it.content) }
            .trim()
            .takeLast(FOLLOW_UP_SOURCE_MAX_CHARS)

        launchChatOperation {
            _uiState.update {
                it.copy(
                    currentInput = "",
                    isGenerating = true,
                    error = null,
                    errorRes = null,
                    followUpTarget = null,
                    lastGenerationStats = null
                )
            }

            var session = _uiState.value.chatSession.addUserMessage(input)

            if (session.messages.size == 1 && session.systemPrompt.isBlank()) {
                val aiContext = getAiContextUseCase()
                session = session.copy(systemPrompt = aiContext)
            }

            session = session.addAssistantMessage("")
            _uiState.update { it.copy(chatSession = session) }
            repository.setActiveSession(session)
            repository.saveSession(session)

            try {
                val responseBuilder = StringBuilder()
                var lastUiPush = 0L
                val sendStartMs = System.currentTimeMillis()
                var firstChunkMs = 0L
                var chunkCount = 0
                repository.sendMessage().collect { partial ->
                    responseBuilder.append(partial)
                    val now = System.currentTimeMillis()
                    chunkCount++
                    if (firstChunkMs == 0L) firstChunkMs = now
                    if (now - lastUiPush >= STREAM_UI_UPDATE_MS) {
                        lastUiPush = now
                        session = session.updateLastAssistantMessage(responseBuilder.toString().trimStart())
                        _uiState.update { it.copy(chatSession = session) }
                        repository.setActiveSession(session)
                    }
                }
                session = session.updateLastAssistantMessage(responseBuilder.toString().trimStart())
                _uiState.update { it.copy(chatSession = session) }
                repository.setActiveSession(session)
                repository.saveSession(session)

                if (BuildConfig.DEBUG && chunkCount > 0) {
                    val ttftSec = (firstChunkMs - sendStartMs) / 1000.0
                    val decodeMs = (System.currentTimeMillis() - firstChunkMs).coerceAtLeast(1L)
                    val chunksPerSec = (chunkCount - 1) * 1000.0 / decodeMs
                    _uiState.update {
                        it.copy(
                            lastGenerationStats = String.format(
                                Locale.getDefault(),
                                "TTFT %.1f s · %.1f chunks/s · %d chunks",
                                ttftSec, chunksPerSec, chunkCount
                            )
                        )
                    }
                }

                val directRoutineIntent = routineCreationIntentUseCase(input)
                val directShoppingIntent = shoppingCreationIntentUseCase(input)
                val isShortConfirmation = routineCreationIntentUseCase.isFollowUpConfirmation(input)
                val followUpRoutineIntent = isShortConfirmation && !directRoutineIntent &&
                    routineCreationIntentUseCase.looksLikeRoutineProposal(previousAssistantProse)
                val followUpShoppingIntent = isShortConfirmation && !directShoppingIntent &&
                    shoppingCreationIntentUseCase.looksLikeShoppingProposal(previousAssistantProse)

                val wantRoutineExtraction = directRoutineIntent || followUpRoutineIntent
                val wantShoppingExtraction = directShoppingIntent || followUpShoppingIntent
                val extraSource = previousAssistantProse.takeIf {
                    it.isNotBlank() && (followUpRoutineIntent || followUpShoppingIntent)
                }
                if (wantRoutineExtraction) {
                    session = extractSuggestionsInto(
                        session,
                        AiStructuredBlocks.ROUTINES_MARKER,
                        extraSource = extraSource
                    ) {
                        repository.extractRoutines(it)
                    }
                }
                if (wantShoppingExtraction) {
                    session = extractSuggestionsInto(
                        session,
                        AiStructuredBlocks.SHOPPING_MARKER,
                        extraSource = extraSource
                    ) {
                        repository.extractShopping(it)
                    }
                }

                parseAndStoreSuggestions(session)
                session = maybeGenerateTitle(session, input)
                recomputeContextUsage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message, errorRes = R.string.ai_error_generate)
                }
            } finally {
                _uiState.update { it.copy(isGenerating = false, isExtractingSuggestions = false) }
            }
        }
    }

    fun onStopGeneration() {
        if (!_uiState.value.isGenerating) return
        launchChatOperation {
            repository.saveSession(_uiState.value.chatSession)
        }
    }

    /** Parses structured data blocks in assistant messages and stores suggestions by message ID. */
    private fun parseAndStoreSuggestions(session: AiChatSession) {
        val assistantMessages = session.messages
            .filter { it.role is MessageRole.Assistant }

        val shopping: Map<String, List<AiShoppingSuggestion>> = assistantMessages
            .associate { it.id to parseAiShoppingListUseCase(it.content) }
            .filterValues { it.isNotEmpty() }

        val routines: Map<String, List<AiRoutineSuggestion>> = assistantMessages
            .associate { it.id to parseAiRoutinesUseCase(it.content) }
            .filterValues { it.isNotEmpty() }

        val followUpTarget = assistantMessages.lastOrNull()?.let { msg ->
            val needsRoutines = routines[msg.id].isNullOrEmpty() &&
                routineCreationIntentUseCase.looksLikeRoutineProposal(msg.content)
            val needsShopping = shopping[msg.id].isNullOrEmpty() &&
                shoppingCreationIntentUseCase.looksLikeShoppingProposal(msg.content)
            when {
                needsRoutines && needsShopping -> FollowUpTarget.BOTH
                needsRoutines -> FollowUpTarget.ROUTINES
                needsShopping -> FollowUpTarget.SHOPPING
                else -> null
            }
        }

        Log.d(
            tag,
            "Suggestions after extraction: routines=${routines.values.sumOf { it.size }}, " +
                "shopping=${shopping.values.sumOf { it.size }}"
        )
        _uiState.update {
            it.copy(
                shoppingSuggestions = shopping,
                routineSuggestions = routines,
                followUpTarget = followUpTarget
            )
        }
    }

    /**
     * Primer intercambio completado: pide al modelo un título corto para la sesión (una
     * llamada efímera barata, solo una vez por chat). Si falla o devuelve vacío, se queda
     * el recorte del primer mensaje que puso [AiChatSession.addUserMessage].
     */
    private suspend fun maybeGenerateTitle(session: AiChatSession, userInput: String): AiChatSession {
        if (session.messages.size != 2) return session
        val reply = session.messages.lastOrNull()
            ?.let { AiStructuredBlocks.stripFromDisplay(it.content) }
            .orEmpty()
        if (reply.isBlank()) return session

        val title = try {
            repository.generateSessionTitle(userInput, reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Fallo generando el título de la sesión", e)
            ""
        }
        if (title.isBlank()) return session

        val titled = session.copy(title = title)
        _uiState.update { it.copy(chatSession = titled) }
        repository.setActiveSession(titled)
        repository.saveSession(titled)
        return titled
    }

    /**
     * Turno 2: en una llamada aislada y a baja temperatura, extrae del texto ya generado los datos
     * estructurados ([extract]) y los adjunta al mensaje como bloque oculto ([marker]). Así el
     * parseo y la recarga de la sesión siguen funcionando igual (la UI oculta el marcador).
     */
    private suspend fun extractSuggestionsInto(
        session: AiChatSession,
        marker: String,
        extraSource: String? = null,
        extract: suspend (String) -> String
    ): AiChatSession {
        val lastAssistant = session.messages.lastOrNull { it.role is MessageRole.Assistant }
            ?: return session
        val prose = AiStructuredBlocks.stripFromDisplay(lastAssistant.content).trim()
        if (prose.isBlank()) return session

        val source = listOfNotNull(extraSource?.takeIf { it.isNotBlank() }, prose)
            .joinToString("\n\n")

        _uiState.update { it.copy(isExtractingSuggestions = true) }
        val json = try {
            extract(source)
        } finally {
            _uiState.update { it.copy(isExtractingSuggestions = false) }
        }
        if (!json.contains('{')) return session

        val enriched = lastAssistant.content + "\n\n$marker ${json.trim()}"
        val updated = session.updateLastAssistantMessage(enriched)
        _uiState.update { it.copy(chatSession = updated) }
        repository.setActiveSession(updated)
        repository.saveSession(updated)
        return updated
    }

    fun onAddSuggestionsToList(messageId: String) {
        val state = _uiState.value
        val items = state.shoppingSuggestions[messageId] ?: return
        if (messageId in state.addedSuggestionMessageIds || state.addingSuggestionMessageId != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(addingSuggestionMessageId = messageId) }
            addAiItemsToShoppingListUseCase(items).fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            addingSuggestionMessageId = null,
                            addedSuggestionMessageIds = it.addedSuggestionMessageIds + messageId,
                            addedToListCount = count
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            addingSuggestionMessageId = null,
                            error = e.message,
                            errorRes = R.string.ai_error_add_to_list
                        )
                    }
                }
            )
        }
    }

    fun onAddedToListShown() {
        _uiState.update { it.copy(addedToListCount = null) }
    }

    fun onAddRoutineSuggestions(messageId: String, type: RoutineType) {
        val state = _uiState.value
        val routines = state.routineSuggestions[messageId] ?: return
        if (messageId in state.addedRoutineMessageIds || state.addingRoutineMessageId != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(addingRoutineMessageId = messageId) }
            addAiRoutinesUseCase(routines, type).fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            addingRoutineMessageId = null,
                            addedRoutineMessageIds = it.addedRoutineMessageIds + messageId,
                            addedRoutinesCount = count
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            addingRoutineMessageId = null,
                            error = e.message,
                            errorRes = R.string.ai_error_create_routines
                        )
                    }
                }
            )
        }
    }

    fun onAddedRoutinesShown() {
        _uiState.update { it.copy(addedRoutinesCount = null) }
    }

    fun onQuickPrompt(prompt: String) {
        _uiState.update { it.copy(currentInput = prompt) }
        onSendMessage()
    }

    fun onFollowUpChipTapped(userText: String, ackText: String) {
        if (_uiState.value.isGenerating) return
        val target = _uiState.value.followUpTarget ?: return

        val source = _uiState.value.chatSession.messages
            .filter { it.role is MessageRole.Assistant }
            .takeLast(FOLLOW_UP_SOURCE_MESSAGES)
            .joinToString("\n\n") { AiStructuredBlocks.stripFromDisplay(it.content) }
            .trim()
            .takeLast(FOLLOW_UP_SOURCE_MAX_CHARS)

        launchChatOperation {
            _uiState.update { it.copy(followUpTarget = null, error = null, errorRes = null) }

            var session = _uiState.value.chatSession
                .addUserMessage(userText)
                .addAssistantMessage(ackText)
            val ackMessageId = session.messages.last().id
            _uiState.update { it.copy(chatSession = session) }
            repository.setActiveSession(session)
            repository.saveSession(session)

            try {
                if (target.includesRoutines) {
                    session = extractSuggestionsInto(
                        session,
                        AiStructuredBlocks.ROUTINES_MARKER,
                        extraSource = source
                    ) { repository.extractRoutines(it) }
                }
                if (target.includesShopping) {
                    session = extractSuggestionsInto(
                        session,
                        AiStructuredBlocks.SHOPPING_MARKER,
                        extraSource = source
                    ) { repository.extractShopping(it) }
                }
                parseAndStoreSuggestions(session)
                recomputeContextUsage()

                val produced = _uiState.value.shoppingSuggestions[ackMessageId].orEmpty().isNotEmpty() ||
                    _uiState.value.routineSuggestions[ackMessageId].orEmpty().isNotEmpty()
                if (!produced) {
                    _uiState.update {
                        it.copy(
                            followUpTarget = target,
                            errorRes = R.string.ai_error_prepare_card_retry
                        )
                    }
                }

                repository.resetSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        followUpTarget = target,
                        error = e.message,
                        errorRes = R.string.ai_error_prepare_card
                    )
                }
            } finally {
                _uiState.update { it.copy(isExtractingSuggestions = false) }
            }
        }
    }

    fun onDownloadModel(wifiOnly: Boolean = false) {
        val model = _uiState.value.selectedModel
        when (model?.compatibilityWith(_uiState.value.deviceRamBytes)) {
            ModelCompatibility.Unsupported -> {
                _uiState.update { it.copy(errorRes = R.string.ai_model_unsupported_error) }
                return
            }
            ModelCompatibility.Tight -> {
                _uiState.update { it.copy(pendingTightDownloadModel = model) }
                return
            }
            else -> Unit
        }
        startDownload(wifiOnly)
    }

    fun onConfirmTightDownload() {
        _uiState.update { it.copy(pendingTightDownloadModel = null) }
        startDownload(lastDownloadWifiOnly)
    }

    fun onDismissTightDownload() {
        _uiState.update { it.copy(pendingTightDownloadModel = null) }
    }

    private fun startDownload(wifiOnly: Boolean) {
        lastDownloadWifiOnly = wifiOnly
        viewModelScope.launch {
            try {
                repository.downloadModel(wifiOnly)
            } catch (e: Exception) {
                Log.e(tag, "Error downloading model", e)
                _uiState.update {
                    it.copy(errorRes = R.string.ai_error_download)
                }
            }
        }
    }

    fun onRetryAfterLoadCrash() {
        val modelId = _uiState.value.selectedModel?.id ?: return
        repository.clearLoadFailures(modelId)
    }

    fun onRetryDownload() {
        _uiState.update { it.copy(error = null) }
        onDownloadModel(lastDownloadWifiOnly)
    }

    fun onCancelDownload() {
        repository.cancelDownload()
    }

    fun onDeleteModel(modelId: String) {
        viewModelScope.launch {
            repository.deleteModel(modelId)
            refreshDownloadedModels()
        }
    }

    private fun refreshDownloadedModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadedModelIds = repository.getDownloadedModelIds()) }
        }
    }

    fun onNewChat() {
        refreshQuickPrompts()
        launchChatOperation { startNewSession() }
    }

    private suspend fun startNewSession() {
        val newSession = AiChatSession(modelId = _uiState.value.selectedModel?.id ?: "")
        repository.setActiveSession(newSession)
        repository.resetSession()
        repository.saveSession(newSession)
        _uiState.update {
            it.copy(
                chatSession = newSession,
                error = null,
                shoppingSuggestions = emptyMap(),
                addedSuggestionMessageIds = emptySet(),
                addingSuggestionMessageId = null,
                routineSuggestions = emptyMap(),
                addedRoutineMessageIds = emptySet(),
                addingRoutineMessageId = null,
                followUpTarget = null,
                lastGenerationStats = null,
                contextUsage = 0f
            )
        }
    }

    fun onLoadChat(sessionId: String) {
        launchChatOperation {
            val session = repository.getSession(sessionId) ?: return@launchChatOperation
            repository.setActiveSession(session)
            if (session.modelId != _uiState.value.selectedModel?.id) {
                repository.selectModel(session.modelId)
            }
            _uiState.update {
                it.copy(
                    chatSession = session,
                    error = null,
                    addedSuggestionMessageIds = emptySet(),
                    addingSuggestionMessageId = null,
                    addedRoutineMessageIds = emptySet(),
                    addingRoutineMessageId = null,
                    lastGenerationStats = null
                )
            }
            parseAndStoreSuggestions(session)
            recomputeContextUsage()

            var refreshed = session
            if (session.systemPrompt.isNotBlank()) {
                val fresh = getAiContextUseCase()
                if (fresh.isNotBlank() && fresh != session.systemPrompt) {
                    refreshed = session.copy(systemPrompt = fresh)
                    repository.setActiveSession(refreshed)
                    repository.saveSession(refreshed)
                    _uiState.update { it.copy(chatSession = refreshed) }
                }
            }
            repository.resetSession()
        }
    }

    fun onDeleteChat(sessionId: String) {
        launchChatOperation {
            repository.deleteSession(sessionId)
            if (_uiState.value.chatSession.id == sessionId) {
                refreshQuickPrompts()
                startNewSession()
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null, errorRes = null) }
    }

    fun onCompactContext() {
        val session = _uiState.value.chatSession
        if (_uiState.value.isCompacting || _uiState.value.isGenerating) return
        if (session.messages.size - session.summarizedUpTo <= KEEP_RECENT_MESSAGES) return

        launchChatOperation {
            _uiState.update { it.copy(isCompacting = true, error = null) }
            try {
                val summary = repository.summarizeConversation(buildCompactionSource(session))
                if (summary.isBlank()) {
                    _uiState.update { it.copy(errorRes = R.string.ai_error_compact) }
                    return@launchChatOperation
                }
                val compacted = session.copy(
                    contextSummary = summary,
                    summarizedUpTo = (session.messages.size - KEEP_RECENT_MESSAGES).coerceAtLeast(0)
                )
                repository.setActiveSession(compacted)
                repository.saveSession(compacted)
                _uiState.update { it.copy(chatSession = compacted, contextCompacted = true) }
                recomputeContextUsage()
                repository.resetSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, errorRes = R.string.ai_error_compact) }
            } finally {
                _uiState.update { it.copy(isCompacting = false) }
            }
        }
    }

    fun onContextCompactedShown() {
        _uiState.update { it.copy(contextCompacted = false) }
    }

    /**
     * Reporta una respuesta del asistente como ofensiva o inapropiada (requisito de la
     * política de contenido generado por IA de Google Play). En su propio job: no toca el
     * engine, así que no debe serializarse con las operaciones de chat.
     */
    fun onReportMessage(messageId: String) {
        val message = _uiState.value.chatSession.messages.find { it.id == messageId } ?: return
        viewModelScope.launch {
            val result = reportAiMessageUseCase(message, _uiState.value.chatSession.modelId)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        reportResult = true,
                        reportedMessageIds = state.reportedMessageIds + messageId
                    )
                } else {
                    state.copy(reportResult = false)
                }
            }
        }
    }

    fun onReportResultShown() {
        _uiState.update { it.copy(reportResult = null) }
    }

    /**
     * Fuente para el resumen: los mensajes antiguos (desde donde acabó el resumen previo hasta
     * dejar [KEEP_RECENT_MESSAGES] recientes), sin bloques y como transcripción. Si ya había un
     * resumen, se antepone ("resumir el resumen"). Acotada por el final.
     */
    private fun buildCompactionSource(session: AiChatSession): String {
        val older = session.messages
            .drop(session.summarizedUpTo)
            .dropLast(KEEP_RECENT_MESSAGES)
        val transcript = older.joinToString("\n\n") { msg ->
            val who = if (msg.role is MessageRole.User) "Usuario" else "Asistente"
            "$who: ${AiStructuredBlocks.stripFromDisplay(msg.content)}"
        }
        return listOfNotNull(
            session.contextSummary.takeIf { it.isNotBlank() }?.let { "Resumen previo:\n$it" },
            transcript.takeIf { it.isNotBlank() }
        ).joinToString("\n\n").takeLast(COMPACTION_SOURCE_MAX_CHARS)
    }

    /** Recalcula la fracción de contexto ocupada tras cambiar la conversación. */
    private fun recomputeContextUsage() {
        val maxTokens = _uiState.value.selectedModel?.maxTokens ?: DEFAULT_MAX_TOKENS
        val usage = estimateContextUsageUseCase(_uiState.value.chatSession, maxTokens)
        _uiState.update { it.copy(contextUsage = usage) }
    }

    private companion object {
        /** Intervalo mínimo entre refrescos de UI durante el streaming (~12 fps de texto). */
        const val STREAM_UI_UPDATE_MS = 80L

        /** Mensajes recientes que la compactación conserva literales (los demás se resumen). */
        const val KEEP_RECENT_MESSAGES = 4

        /** Tope de la transcripción que se manda al resumen (recortada por el final). */
        const val COMPACTION_SOURCE_MAX_CHARS = 8000

        /** Presupuesto de tokens por defecto si aún no hay modelo seleccionado. */
        const val DEFAULT_MAX_TOKENS = 4096

        /** Mensajes del asistente que entran como fuente extra en las extracciones de
         *  seguimiento (2: tras "parar" + "continúa" la propuesta queda repartida en dos). */
        const val FOLLOW_UP_SOURCE_MESSAGES = 2

        /** Tope de la fuente extra, recortando por el principio (los ítems van al final). */
        const val FOLLOW_UP_SOURCE_MAX_CHARS = 4000
    }
}
