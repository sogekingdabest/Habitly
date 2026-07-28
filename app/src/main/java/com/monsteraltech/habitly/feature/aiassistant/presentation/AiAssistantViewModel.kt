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

    /** Last network choice from the download dialog; "retry" reuses it. */
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
                // The state changes when downloads finish or are cancelled, on delete and on model
                // change: the moment to refresh which models are on disk.
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
     * Recomputes the suggestion chips from the current household state. Runs in its own job
     * because it reads the shopping list and the routines, and must not delay loading the chat.
     */
    private fun refreshQuickPrompts() {
        viewModelScope.launch {
            val prompts = getContextualQuickPromptsUseCase()
            _uiState.update { it.copy(quickPrompts = prompts) }
        }
    }

    /**
     * Serialises every operation that touches the engine (send, stop, new chat, load, model
     * change): each one cancels **and waits for** the previous one before starting, so two chat
     * operations never overlap.
     *
     * Note this only serialises coroutines. The **real** stop of the native inference lives in the
     * repository — cancelling the stream fires `cancelProcess()`, and every close or recreate
     * waits for the engine to confirm. Without that piece the join completes when the coroutine
     * dies while the native decode is still running.
     */
    private fun launchChatOperation(block: suspend CoroutineScope.() -> Unit) {
        val previous = chatOperationJob
        chatOperationJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            block()
        }
    }

    fun onSelectModel(modelId: String) {
        // A model that does not fit the device never even gets selected: selecting it would leave
        // the screen in a dead end, with no chat and no possible download.
        val model = _uiState.value.availableModels.find { it.id == modelId } ?: return
        if (!model.compatibilityWith(_uiState.value.deviceRamBytes).canUse) {
            _uiState.update { it.copy(errorRes = R.string.ai_model_unsupported_error) }
            return
        }
        // Changing model closes the engine: the running generation is cut and we **wait** for the
        // stream to release the native conversation before the repository closes the engine.
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

        // The follow-up gates need what the assistant last said **before** the new messages are
        // appended. Two messages, not one: after a stop followed by "carry on", the proposal ends
        // up split across both, and taking only the last would lose its first half. Trimmed from
        // the end, where the items live, so the extractor's context does not overflow.
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

            // On the first message, inject the system instruction into the session object.
            // The repository will then use it to configure the LiteRT-LM conversation.
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
                    // Throttle: drawing every token recomposes the list and re-parses the whole
                    // message's markdown, which is quadratic. At ~12 refreshes a second the stream
                    // looks just as alive, and the full text always lands in the final flush.
                    if (now - lastUiPush >= STREAM_UI_UPDATE_MS) {
                        lastUiPush = now
                        session = session.updateLastAssistantMessage(responseBuilder.toString().trimStart())
                        _uiState.update { it.copy(chatSession = session) }
                        repository.setActiveSession(session)
                    }
                }
                // Final flush: guarantees the stream's tail, below the throttle threshold, is both
                // drawn and persisted.
                session = session.updateLastAssistantMessage(responseBuilder.toString().trimStart())
                _uiState.update { it.copy(chatSession = session) }
                repository.setActiveSession(session)
                repository.saveSession(session)

                // Generation metrics, debug builds only: time to first token and decode speed,
                // for comparing GPU/MTP against CPU.
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

                // Second turn: extract routines and/or a shopping list from the text just
                // generated, but only when the user asked for it, which keeps spurious cards away.
                // Typed input goes through the direct gate (the message asks to create) or the
                // follow-up gate (a short confirmation after a proposal). The follow-up chip does
                // **not** come through here: it takes the fast path in [onFollowUpChip], skipping
                // the conversational turn.
                val directRoutineIntent = routineCreationIntentUseCase(input)
                val directShoppingIntent = shoppingCreationIntentUseCase(input)
                val isShortConfirmation = routineCreationIntentUseCase.isFollowUpConfirmation(input)
                val followUpRoutineIntent = isShortConfirmation && !directRoutineIntent &&
                    routineCreationIntentUseCase.looksLikeRoutineProposal(previousAssistantProse)
                val followUpShoppingIntent = isShortConfirmation && !directShoppingIntent &&
                    shoppingCreationIntentUseCase.looksLikeShoppingProposal(previousAssistantProse)

                val wantRoutineExtraction = directRoutineIntent || followUpRoutineIntent
                val wantShoppingExtraction = directShoppingIntent || followUpShoppingIntent
                // On follow-ups the proposal lives in the **previous** messages, since the reply to
                // a confirmation may not repeat it; they join as an extra source.
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
                // Cancellation (stop answer, new chat): not a generation error.
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

    /**
     * Stops the running generation while keeping the partial text already on screen, the same way
     * a chat app's stop button behaves.
     *
     * Cancelling the send job makes the repository send `cancelProcess()` to the engine — the
     * coroutine cancellation alone does **not** stop it, since litertlm's Flow has an empty
     * awaitClose — and mark the conversation for recreation; the partial text is then persisted.
     * A send or a "new chat" immediately after waits in the repository for the engine to confirm
     * the stop before closing or recreating the conversation, which is what used to hang it.
     */
    fun onStopGeneration() {
        if (!_uiState.value.isGenerating) return
        launchChatOperation {
            repository.saveSession(_uiState.value.chatSession)
        }
    }

    /**
     * Scans the assistant messages for the structured blocks (shopping list and routines) and
     * stores the suggestions by message id so their cards can be drawn.
     */
    private fun parseAndStoreSuggestions(session: AiChatSession) {
        val assistantMessages = session.messages
            .filter { it.role is MessageRole.Assistant }

        val shopping: Map<String, List<AiShoppingSuggestion>> = assistantMessages
            .associate { it.id to parseAiShoppingListUseCase(it.content) }
            .filterValues { it.isNotEmpty() }

        val routines: Map<String, List<AiRoutineSuggestion>> = assistantMessages
            .associate { it.id to parseAiRoutinesUseCase(it.content) }
            .filterValues { it.isNotEmpty() }

        // Follow-up chip: the assistant proposed something in prose but no card appeared, either
        // because the gate did not open (e.g. "carry on where you left off") or because the
        // extraction found nothing. The target is decided **here**, by looking at what the message
        // proposes, so the chip never offers "create routines" after a shopping list. If it
        // proposes both, the chip is generic and fires both extractions — the one that does not
        // apply returns empty and produces no card.
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

        // Local telemetry: how many suggestions survive extraction, as a reliability signal.
        Log.d(
            tag,
            "Sugerencias tras extracción: rutinas=${routines.values.sumOf { it.size }}, " +
                "compra=${shopping.values.sumOf { it.size }}"
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
     * First exchange completed: asks the model for a short session title — a cheap ephemeral call,
     * once per chat. If it fails or comes back empty, the truncated first message that
     * [AiChatSession.addUserMessage] set stays.
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
     * Second turn: in an isolated, low-temperature call, extracts the structured data ([extract])
     * from the text already generated and appends it to the message as a hidden block ([marker]),
     * so parsing and session reloading keep working unchanged — the UI hides the marker.
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

        // On follow-ups ("yes, create them") the proposal lives in the previous message, which
        // joins as an extra source so extraction does not depend on the confirmation reply
        // repeating the routines.
        val source = listOfNotNull(extraSource?.takeIf { it.isNotBlank() }, prose)
            .joinToString("\n\n")

        _uiState.update { it.copy(isExtractingSuggestions = true) }
        val json = try {
            extract(source)
        } finally {
            _uiState.update { it.copy(isExtractingSuggestions = false) }
        }
        // With no usable JSON the message is left alone.
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

    /** Creates the routines the assistant proposed in the given message. */
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

    /**
     * Fast path for the follow-up chip ("Yes, add them"). Instead of sending another turn to the
     * model — which would re-narrate the whole proposal and take tens of seconds — it appends a
     * short confirmation and runs the extraction turn directly over the proposal already sitting
     * in the previous messages. The card hangs off the confirmation.
     *
     * [userText] and [ackText] arrive already localised from the screen; the extraction target is
     * read from [AiAssistantUiState.followUpTarget].
     */
    fun onFollowUpChipTapped(userText: String, ackText: String) {
        if (_uiState.value.isGenerating) return
        val target = _uiState.value.followUpTarget ?: return

        // Same source as the follow-up extractions in [onSendMessage]: the last assistant messages
        // without their blocks, trimmed from the end where the items live.
        val source = _uiState.value.chatSession.messages
            .filter { it.role is MessageRole.Assistant }
            .takeLast(FOLLOW_UP_SOURCE_MESSAGES)
            .joinToString("\n\n") { AiStructuredBlocks.stripFromDisplay(it.content) }
            .trim()
            .takeLast(FOLLOW_UP_SOURCE_MAX_CHARS)

        launchChatOperation {
            _uiState.update { it.copy(followUpTarget = null, error = null, errorRes = null) }

            // A visible exchange with **no** model involved: the user's confirmation and a short ack.
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

                // Did a card actually appear? If the extraction found nothing there is neither card
                // nor chip, so the chip is restored and the user is told, allowing a retry.
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

                // The native conversation never saw this exchange, so the next send must rebuild it
                // from the persisted session instead of reusing it.
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

    /**
     * Download behind a memory gate. A model that does not fit is not downloaded at all — hundreds
     * of megabytes or gigabytes for something that would kill the app on load — and one that is
     * borderline asks for confirmation first.
     */
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

    /** The user accepted the "tight on memory" warning: download anyway. */
    fun onConfirmTightDownload() {
        _uiState.update { it.copy(pendingTightDownloadModel = null) }
        startDownload(lastDownloadWifiOnly)
    }

    fun onDismissTightDownload() {
        _uiState.update { it.copy(pendingTightDownloadModel = null) }
    }

    private fun startDownload(wifiOnly: Boolean) {
        // The network choice is remembered so "retry" does not ask again.
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

    /**
     * Explicit retry after a load failure that took the process down. Only the user's button fires
     * it: the automatic ban exists precisely so it does not retry by itself.
     */
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

    /** Deletes the model from disk; if it was the selected one, the download card returns. */
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
        // The household may have changed since the screen opened (list, routines).
        refreshQuickPrompts()
        launchChatOperation { startNewSession() }
    }

    /**
     * Starts an empty session and leaves it active. `suspend` rather than launching its own job so
     * it can be reused after deleting the open chat without chaining another cancellation onto the
     * operation already running.
     */
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
            // The session goes active and on screen immediately; the context refresh comes after.
            // If the user sends before it finishes, the send uses the stored context.
            repository.setActiveSession(session)
            // Auto switch model if different
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

            // The hidden context was frozen when the session was born — that day's list, pantry and
            // routines. It is refreshed now, since the conversation is recreated on reload anyway,
            // so the prefill already carries the household's current state.
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
                // The open chat was the deleted one: an empty one is started in the **same** job,
                // not via onNewChat, which would fire another cancellation onto this operation.
                refreshQuickPrompts()
                startNewSession()
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null, errorRes = null) }
    }

    /**
     * Compacts the context: summarises the older part of the conversation and replaces it with
     * that summary, which travels in the system prompt, keeping the last [KEEP_RECENT_MESSAGES]
     * messages verbatim. The **visible** history does not change — every bubble stays — only what
     * is handed to the model. Recreates the native conversation.
     */
    fun onCompactContext() {
        val session = _uiState.value.chatSession
        if (_uiState.value.isCompacting || _uiState.value.isGenerating) return
        // Nothing to compact if there is only the recent tail above the summary.
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
                // The native conversation is rebuilt with the summary plus the recent tail.
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
     * Reports an assistant answer as offensive or inappropriate, required by Google Play's
     * AI-generated content policy. In its own job: it does not touch the engine, so it must not be
     * serialised with the chat operations.
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
     * Source for the summary: the older messages, from where the previous summary ended up to
     * leaving [KEEP_RECENT_MESSAGES] recent ones, stripped of blocks and rendered as a transcript.
     * An existing summary is prepended, i.e. the summary gets summarised. Trimmed from the end.
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

    /** Recomputes the fraction of context in use after the conversation changes. */
    private fun recomputeContextUsage() {
        val maxTokens = _uiState.value.selectedModel?.maxTokens ?: DEFAULT_MAX_TOKENS
        val usage = estimateContextUsageUseCase(_uiState.value.chatSession, maxTokens)
        _uiState.update { it.copy(contextUsage = usage) }
    }

    private companion object {
        /** Minimum interval between UI refreshes while streaming (~12 text fps). */
        const val STREAM_UI_UPDATE_MS = 80L

        /** Recent messages compaction keeps verbatim; everything older is summarised. */
        const val KEEP_RECENT_MESSAGES = 4

        /** Cap on the transcript sent to the summariser (trimmed from the end). */
        const val COMPACTION_SOURCE_MAX_CHARS = 8000

        /** Default token budget when no model has been selected yet. */
        const val DEFAULT_MAX_TOKENS = 4096

        /** Assistant messages used as an extra source in follow-up extractions. Two, because after
         *  a stop followed by "carry on" the proposal ends up split across both. */
        const val FOLLOW_UP_SOURCE_MESSAGES = 2

        /** Cap on the extra source, trimming from the start since the items sit at the end. */
        const val FOLLOW_UP_SOURCE_MAX_CHARS = 4000
    }
}
