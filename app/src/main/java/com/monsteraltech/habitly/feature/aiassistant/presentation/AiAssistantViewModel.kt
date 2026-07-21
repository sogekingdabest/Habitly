package com.monsteraltech.habitly.feature.aiassistant.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiItemsToShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetAiContextUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val repository: AiAssistantRepository,
    private val getAiContextUseCase: GetAiContextUseCase,
    private val getContextualQuickPromptsUseCase: GetContextualQuickPromptsUseCase,
    private val parseAiShoppingListUseCase: ParseAiShoppingListUseCase,
    private val addAiItemsToShoppingListUseCase: AddAiItemsToShoppingListUseCase,
    private val parseAiRoutinesUseCase: ParseAiRoutinesUseCase,
    private val addAiRoutinesUseCase: AddAiRoutinesUseCase
) : ViewModel() {

    private val tag = "AiAssistantViewModel"

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private var chatOperationJob: Job? = null

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

    fun onSelectModel(modelId: String) {
        repository.selectModel(modelId)
    }

    fun onInputChange(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun onSendMessage() {
        val input = _uiState.value.currentInput.trim()
        if (input.isBlank() || _uiState.value.isGenerating) return

        chatOperationJob?.cancel()
        chatOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(currentInput = "", isGenerating = true, error = null) }

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
                repository.sendMessage().collect { partial ->
                    responseBuilder.append(partial)
                    // LiteRT-LM handles chat templates internally,
                    // so no manual token cleanup is needed.
                    val text = responseBuilder.toString().trimStart()
                    session = session.updateLastAssistantMessage(text)
                    _uiState.update { it.copy(chatSession = session) }
                    repository.setActiveSession(session)
                }
                repository.saveSession(session)
                parseAndStoreSuggestions(session)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Error al generar respuesta")
                }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
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

        _uiState.update {
            it.copy(shoppingSuggestions = shopping, routineSuggestions = routines)
        }
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
                            error = e.message ?: "No se pudo añadir a la lista"
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
                            error = e.message ?: "No se pudieron crear las rutinas"
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

    fun onDownloadModel() {
        viewModelScope.launch {
            try {
                repository.downloadModel()
            } catch (e: Exception) {
                Log.e(tag, "Error downloading model", e)
                _uiState.update {
                    it.copy(error = "Error descargando modelo: ${e.message}")
                }
            }
        }
    }

    fun onRetryDownload() {
        _uiState.update { it.copy(error = null) }
        onDownloadModel()
    }

    fun onNewChat() {
        // La casa puede haber cambiado desde que se abrió la pantalla (lista, rutinas…).
        refreshQuickPrompts()
        chatOperationJob?.cancel()
        chatOperationJob = viewModelScope.launch {
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
                    addingRoutineMessageId = null
                )
            }
        }
    }

    fun onLoadChat(sessionId: String) {
        chatOperationJob?.cancel()
        chatOperationJob = viewModelScope.launch {
            val session = repository.getSession(sessionId)
            if (session != null) {
                repository.setActiveSession(session)
                repository.resetSession()
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
                        addingRoutineMessageId = null
                    )
                }
                parseAndStoreSuggestions(session)
            }
        }
    }

    fun onDeleteChat(sessionId: String) {
        chatOperationJob?.cancel()
        chatOperationJob = viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.chatSession.id == sessionId) {
                onNewChat()
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
