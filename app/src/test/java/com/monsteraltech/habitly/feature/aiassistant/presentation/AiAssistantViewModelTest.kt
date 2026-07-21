package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakePantryRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiItemsToShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GenerateWeeklyMenuUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetAiContextUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.AddRoutineUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAiAssistantRepository
    private lateinit var viewModel: AiAssistantViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAiAssistantRepository()

        val getContextUseCase = GetAiContextUseCase(
            FakeAuthRepository(),
            FakeHouseholdRepository(),
            FakeRoutinesRepository(),
            FakeShoppingRepository(),
            FakePantryRepository()
        )

        val addItemsUseCase = AddAiItemsToShoppingListUseCase(
            FakeAuthRepository(),
            FakeHouseholdRepository(),
            FakeShoppingRepository()
        )

        val quickPromptsUseCase = GetContextualQuickPromptsUseCase(
            FakeAuthRepository(),
            FakeHouseholdRepository(),
            FakeRoutinesRepository(),
            FakeShoppingRepository(),
            FakePantryRepository(),
            GenerateWeeklyMenuUseCase()
        )

        val addRoutinesUseCase = AddAiRoutinesUseCase(
            FakeAuthRepository(),
            FakeHouseholdRepository(),
            AddRoutineUseCase(FakeRoutinesRepository())
        )

        viewModel = AiAssistantViewModel(
            repository = fakeRepository,
            getAiContextUseCase = getContextUseCase,
            getContextualQuickPromptsUseCase = quickPromptsUseCase,
            parseAiShoppingListUseCase = ParseAiShoppingListUseCase(),
            addAiItemsToShoppingListUseCase = addItemsUseCase,
            parseAiRoutinesUseCase = ParseAiRoutinesUseCase(),
            addAiRoutinesUseCase = addRoutinesUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun advance() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `initial state has empty input and is not generating`() {
        val state = viewModel.uiState.value

        assertEquals("", state.currentInput)
        assertFalse(state.isGenerating)
        assertNull(state.error)
    }

    @Test
    fun `initial state has available models`() {
        val state = viewModel.uiState.value

        assertTrue(state.availableModels.isNotEmpty())
    }

    @Test
    fun `initial state has quick prompts`() {
        val state = viewModel.uiState.value

        assertTrue(state.quickPrompts.isNotEmpty())
    }

    @Test
    fun `onInputChange updates currentInput in state`() {
        viewModel.onInputChange("Hola mundo")

        assertEquals("Hola mundo", viewModel.uiState.value.currentInput)
    }

    @Test
    fun `onSendMessage with blank input does nothing`() {
        viewModel.onInputChange("   ")
        viewModel.onSendMessage()
        advance()

        val state = viewModel.uiState.value
        assertEquals("   ", state.currentInput)
        assertFalse(state.isGenerating)
        assertEquals(0, fakeRepository.sendMessageCallCount)
    }

    @Test
    fun `onSendMessage sends message and clears input`() {
        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        val state = viewModel.uiState.value
        assertEquals("", state.currentInput)
        assertEquals(2, state.chatSession.messages.size)
        assertEquals(1, fakeRepository.sendMessageCallCount)
        assertTrue(fakeRepository.savedSessions.isNotEmpty())
    }

    @Test
    fun `onSendMessage on error sets error state`() {
        fakeRepository.shouldFailSendMessage = true
        fakeRepository.errorMessage = "Fallo del modelo"

        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        val state = viewModel.uiState.value
        assertFalse("isGenerating should be false", state.isGenerating)
        assertEquals("sendMessage should have been called", 1, fakeRepository.sendMessageCallCount)
        assertTrue("Error should be set but was: ${state.error}", state.error != null)
    }

    @Test
    fun `onNewChat creates a new session`() {
        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        viewModel.onNewChat()
        advance()

        val session = viewModel.uiState.value.chatSession
        assertTrue(session.messages.isEmpty())
    }

    @Test
    fun `onNewChat clears error`() {
        fakeRepository.shouldFailSendMessage = true

        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        viewModel.onNewChat()
        advance()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onNewChat resets session in repository`() {
        viewModel.onNewChat()
        advance()

        assertTrue(fakeRepository.resetSessionCallCount > 0)
    }

    @Test
    fun `onLoadChat loads existing session`() {
        val existingSession = AiChatSession().addUserMessage("Mensaje guardado")
        fakeRepository.savedSessions.add(existingSession)

        viewModel.onLoadChat(existingSession.id)
        advance()

        val session = viewModel.uiState.value.chatSession
        assertEquals(existingSession.id, session.id)
        assertEquals(1, session.messages.size)
    }

    @Test
    fun `onLoadChat with non-existent session does nothing`() {
        val initialState = viewModel.uiState.value.chatSession

        viewModel.onLoadChat("non-existent-id")
        advance()

        assertEquals(initialState.id, viewModel.uiState.value.chatSession.id)
    }

    @Test
    fun `onDeleteChat deletes session from repository`() {
        viewModel.onDeleteChat("session-to-delete")
        advance()

        assertTrue(fakeRepository.deletedSessionIds.contains("session-to-delete"))
    }

    @Test
    fun `onQuickPrompt sends message`() {
        viewModel.onQuickPrompt("Prompt rapido")
        advance()

        assertEquals(1, fakeRepository.sendMessageCallCount)
    }

    @Test
    fun `onSelectModel changes selected model`() {
        val newModelId = fakeRepository.getAvailableModels()[1].id

        viewModel.onSelectModel(newModelId)
        advance()

        assertEquals(newModelId, viewModel.uiState.value.selectedModel?.id)
    }

    @Test
    fun `onDismissError clears error`() {
        fakeRepository.shouldFailSendMessage = true

        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        viewModel.onDismissError()

        assertNull(viewModel.uiState.value.error)
    }
}
