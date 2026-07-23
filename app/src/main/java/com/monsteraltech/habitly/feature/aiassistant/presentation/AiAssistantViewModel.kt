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
        _uiState.update { it.copy(availableModels = repository.getAvailableModels()) }

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
                // El estado cambia al terminar/cancelar descargas, borrar o cambiar de
                // modelo: momento de refrescar qué modelos hay en disco.
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
        // Cambiar de modelo cierra el engine: se corta la generación en curso y se ESPERA a
        // que el stream suelte la conversación nativa antes de que el repo cierre el engine.
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

        // Las puertas de seguimiento necesitan lo último que dijo el asistente ANTES de
        // añadir los nuevos mensajes. Van los DOS últimos mensajes: tras "parar" +
        // "continúa", la propuesta (p. ej. la lista) queda repartida en dos y con solo el
        // último la extracción perdería la primera mitad. Acotado por el final, que es
        // donde viven los ítems, para no desbordar el contexto del extractor.
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
                    // Throttle: pintar cada token recompone la lista y re-parsea el markdown
                    // del mensaje entero (coste cuadrático); a ~12 refrescos/s el stream se ve
                    // igual de vivo. El texto completo se vuelca siempre en el flush final.
                    if (now - lastUiPush >= STREAM_UI_UPDATE_MS) {
                        lastUiPush = now
                        session = session.updateLastAssistantMessage(responseBuilder.toString().trimStart())
                        _uiState.update { it.copy(chatSession = session) }
                        repository.setActiveSession(session)
                    }
                }
                // Flush final: garantiza que la cola del stream (bajo el umbral del throttle)
                // queda pintada y persistida.
                session = session.updateLastAssistantMessage(responseBuilder.toString().trimStart())
                _uiState.update { it.copy(chatSession = session) }
                repository.setActiveSession(session)
                repository.saveSession(session)

                // Métricas de generación (solo builds debug): las cifras que piden las
                // métricas del plan (TTFT y velocidad de decode, GPU/MTP vs CPU).
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

                // Segundo turno (NL-to-Format): extraemos rutinas y/o lista de la compra del texto
                // ya generado, solo cuando el usuario lo pidió (evita tarjetas espurias). Lo tecleado
                // pasa por la puerta directa (el mensaje pide crear) o la de seguimiento (confirmación
                // corta tras una propuesta). El chip de seguimiento NO pasa por aquí: va por el camino
                // rápido de [onFollowUpChip], que se salta el turno conversacional.
                val directRoutineIntent = routineCreationIntentUseCase(input)
                val directShoppingIntent = shoppingCreationIntentUseCase(input)
                val isShortConfirmation = routineCreationIntentUseCase.isFollowUpConfirmation(input)
                val followUpRoutineIntent = isShortConfirmation && !directRoutineIntent &&
                    routineCreationIntentUseCase.looksLikeRoutineProposal(previousAssistantProse)
                val followUpShoppingIntent = isShortConfirmation && !directShoppingIntent &&
                    shoppingCreationIntentUseCase.looksLikeShoppingProposal(previousAssistantProse)

                val wantRoutineExtraction = directRoutineIntent || followUpRoutineIntent
                val wantShoppingExtraction = directShoppingIntent || followUpShoppingIntent
                // En los seguimientos la propuesta vive en los mensajes ANTERIORES (la
                // respuesta a la confirmación puede no repetirla): entran como fuente extra.
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
                // Cancelación (parar respuesta, chat nuevo…): no es un error de generación.
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
     * Detiene la generación en curso conservando el texto parcial ya mostrado (mismo
     * comportamiento que el botón de parar de ChatGPT o del AI Edge Gallery). Cancelar el job
     * de envío hace que el repositorio mande `cancelProcess()` al motor (la cancelación de la
     * corrutina por sí sola NO lo para: el Flow de litertlm trae el awaitClose vacío) y marque
     * la conversación para recrearse; después persistimos el texto parcial. Un envío o un
     * "chat nuevo" justo después esperan en el repo a que el motor confirme la parada antes
     * de cerrar/recrear la conversación, que era lo que colgaba el engine.
     */
    fun onStopGeneration() {
        if (!_uiState.value.isGenerating) return
        launchChatOperation {
            repository.saveSession(_uiState.value.chatSession)
        }
    }

    /**
     * Analiza los mensajes del asistente en busca de los bloques estructurados (lista de la
     * compra y rutinas) y guarda las sugerencias por id de mensaje para pintar sus tarjetas.
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

        // Chip de seguimiento: el asistente propuso algo en prosa pero no salió tarjeta (la
        // puerta no se abrió — p. ej. "continúa donde lo dejaste" — o la extracción no
        // encontró nada). El destino se decide AQUÍ mirando qué propone el mensaje, para que
        // el chip nunca ofrezca "crear rutinas" tras una lista de la compra; si propone ambas
        // cosas, el chip es genérico y lanza las dos extracciones (la que no aplique
        // devolverá vacío y no sacará tarjeta).
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

        // Telemetría local: cuántas sugerencias sobreviven a la extracción (para medir fiabilidad).
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

        // En los seguimientos ("sí, créalas") la propuesta vive en el mensaje anterior:
        // entra como fuente adicional para que la extracción no dependa de que la
        // respuesta de confirmación repita las rutinas.
        val source = listOfNotNull(extraSource?.takeIf { it.isNotBlank() }, prose)
            .joinToString("\n\n")

        _uiState.update { it.copy(isExtractingSuggestions = true) }
        val json = try {
            extract(source)
        } finally {
            _uiState.update { it.copy(isExtractingSuggestions = false) }
        }
        // Sin JSON aprovechable no tocamos el mensaje.
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

    /** Crea las rutinas que propuso el asistente en el mensaje indicado. */
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
     * Camino rápido del chip de seguimiento ("Sí, a la lista" / "Sí, créalas"): en vez de
     * mandar otro turno al modelo (que re-narraría toda la propuesta, decenas de segundos),
     * añade una confirmación breve y lanza directamente el turno 2 de extracción sobre la
     * propuesta que ya vive en los mensajes anteriores. La tarjeta cuelga de la confirmación.
     *
     * [userText] (la confirmación visible) y [ackText] (el "voy") llegan ya localizados desde
     * la pantalla; el destino de la extracción se lee de [AiAssistantUiState.followUpTarget].
     */
    fun onFollowUpChipTapped(userText: String, ackText: String) {
        if (_uiState.value.isGenerating) return
        val target = _uiState.value.followUpTarget ?: return

        // Misma fuente que las extracciones de seguimiento de [onSendMessage]: los últimos
        // mensajes del asistente sin bloques, acotados por el final (donde viven los ítems).
        val source = _uiState.value.chatSession.messages
            .filter { it.role is MessageRole.Assistant }
            .takeLast(FOLLOW_UP_SOURCE_MESSAGES)
            .joinToString("\n\n") { AiStructuredBlocks.stripFromDisplay(it.content) }
            .trim()
            .takeLast(FOLLOW_UP_SOURCE_MAX_CHARS)

        launchChatOperation {
            _uiState.update { it.copy(followUpTarget = null, error = null, errorRes = null) }

            // Intercambio visible SIN modelo: la confirmación del usuario y un "voy" corto.
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

                // ¿Salió de verdad la tarjeta? Si la extracción no encontró nada, no hay ni
                // tarjeta ni chip: se repone el chip y se avisa, para poder reintentar.
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

                // La conversación nativa no ha visto este intercambio: que el siguiente envío
                // la recree desde la sesión persistida en vez de reutilizarla.
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
        // Se recuerda la elección de red para que "reintentar" no vuelva a preguntar.
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

    fun onRetryDownload() {
        _uiState.update { it.copy(error = null) }
        onDownloadModel(lastDownloadWifiOnly)
    }

    fun onCancelDownload() {
        repository.cancelDownload()
    }

    /** Borra el modelo del disco; si era el seleccionado, vuelve la tarjeta de descarga. */
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
        // La casa puede haber cambiado desde que se abrió la pantalla (lista, rutinas…).
        refreshQuickPrompts()
        launchChatOperation { startNewSession() }
    }

    /**
     * Arranca una sesión vacía y la deja activa. Es `suspend` (no lanza su propio job) para
     * poder reutilizarse tras borrar el chat abierto sin encadenar otra cancelación sobre la
     * operación que ya está corriendo.
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
            // La sesión queda activa y pintada YA; el refresco de contexto va después (si
            // el usuario envía antes de que termine, el envío usa el contexto guardado).
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

            // El contexto oculto quedó congelado cuando nació la sesión (la lista, despensa
            // y rutinas de aquel día): se refresca ahora, que la conversación se recrea
            // igualmente al recargar, así el prefill ya lleva el estado actual de la casa.
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
                // El chat abierto era el borrado: se arranca uno vacío en el MISMO job (no
                // vía onNewChat, que relanzaría otra cancelación sobre esta operación).
                refreshQuickPrompts()
                startNewSession()
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null, errorRes = null) }
    }

    /**
     * Compacta el contexto: resume la parte antigua de la conversación y la sustituye por ese
     * resumen (que viaja en el system prompt), conservando literales los últimos
     * [KEEP_RECENT_MESSAGES] mensajes. El historial VISIBLE no cambia (todas las burbujas
     * siguen ahí): solo cambia lo que se le pasa al modelo. Recrea la conversación nativa.
     */
    fun onCompactContext() {
        val session = _uiState.value.chatSession
        if (_uiState.value.isCompacting || _uiState.value.isGenerating) return
        // Nada que compactar si no hay más que la cola reciente por encima del resumen.
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
                // La conversación nativa se recrea ya con el resumen + la cola reciente.
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
