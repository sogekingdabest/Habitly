package com.monsteraltech.habitly.feature.aiassistant.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiItemsToShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GenerateRecipeSuggestionsUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GenerateShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GenerateWeeklyMenuUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetAiContextUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.QuickPrompt
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
    private val generateRecipeSuggestionsUseCase: GenerateRecipeSuggestionsUseCase,
    private val generateShoppingListUseCase: GenerateShoppingListUseCase,
    private val generateWeeklyMenuUseCase: GenerateWeeklyMenuUseCase,
    private val parseAiShoppingListUseCase: ParseAiShoppingListUseCase,
    private val addAiItemsToShoppingListUseCase: AddAiItemsToShoppingListUseCase
) : ViewModel() {

    private val tag = "AiAssistantViewModel"

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private var chatOperationJob: Job? = null

    init {
        _uiState.update {
            it.copy(
                availableModels = repository.getAvailableModels(),
                quickPrompts = listOf(
                    QuickPrompt("Menú semanal", generateWeeklyMenuUseCase()),
                    QuickPrompt("Recetas con pollo", generateRecipeSuggestionsUseCase()),
                    QuickPrompt("Lista semanal", generateShoppingListUseCase()),
                    QuickPrompt("Recetas vegetarianas", "Que puedo cocinar con huevos y patatas?"),
                    QuickPrompt("Cena rapida", "Dame ideas de cenas rapidas y faciles")
                )
            )
        }

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
     * Analiza los mensajes del asistente en busca del bloque estructurado de la lista
     * y guarda las sugerencias por id de mensaje para pintar el botón "Añadir a la lista".
     */
    private fun parseAndStoreSuggestions(session: AiChatSession) {
        val suggestions: Map<String, List<AiShoppingSuggestion>> = session.messages
            .asSequence()
            .filter { it.role is MessageRole.Assistant }
            .map { it.id to parseAiShoppingListUseCase(it.content) }
            .filter { it.second.isNotEmpty() }
            .toMap()
        _uiState.update { it.copy(shoppingSuggestions = suggestions) }
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
                    addingSuggestionMessageId = null
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
                        addingSuggestionMessageId = null
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
