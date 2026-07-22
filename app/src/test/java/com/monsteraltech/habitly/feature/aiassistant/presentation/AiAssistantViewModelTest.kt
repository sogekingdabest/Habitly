package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeHouseholdRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakePantryRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeRoutinesRepository
import com.monsteraltech.habitly.feature.aiassistant.data.repository.FakeShoppingRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.FollowUpTarget
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiItemsToShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.EstimateContextUsageUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GenerateWeeklyMenuUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetAiContextUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.RoutineCreationIntentUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ShoppingCreationIntentUseCase
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
            addAiRoutinesUseCase = addRoutinesUseCase,
            routineCreationIntentUseCase = RoutineCreationIntentUseCase(),
            shoppingCreationIntentUseCase = ShoppingCreationIntentUseCase(),
            estimateContextUsageUseCase = EstimateContextUsageUseCase()
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
    fun `onDeleteModel delegates to repository`() {
        viewModel.onDeleteModel("gemma-4-e2b")
        advance()

        assertEquals(listOf("gemma-4-e2b"), fakeRepository.deletedModelIds)
    }

    @Test
    fun `onCancelDownload delegates to repository`() {
        viewModel.onCancelDownload()

        assertEquals(1, fakeRepository.cancelDownloadCallCount)
    }

    @Test
    fun `retry download keeps the wifi choice`() {
        viewModel.onDownloadModel(wifiOnly = true)
        advance()

        viewModel.onRetryDownload()
        advance()

        assertEquals(true, fakeRepository.lastDownloadWifiOnly)
    }

    @Test
    fun `short confirmation after a proposal triggers routine extraction`() {
        fakeRepository.cannedReply = "Te propongo estas rutinas para casa: fregar y barrer a diario"

        viewModel.onInputChange("dame ideas para organizarme")
        viewModel.onSendMessage()
        advance()
        // La consulta inicial no dispara la puerta directa.
        assertEquals(0, fakeRepository.extractRoutinesCallCount)

        viewModel.onInputChange("sí, créalas")
        viewModel.onSendMessage()
        advance()
        // La confirmación corta sí, gracias a la puerta de seguimiento.
        assertEquals(1, fakeRepository.extractRoutinesCallCount)
    }

    @Test
    fun `a proposal without card shows the follow-up chip`() {
        fakeRepository.cannedReply = "Te propongo estas rutinas para casa: fregar y barrer a diario"

        viewModel.onInputChange("dame ideas para organizarme")
        viewModel.onSendMessage()
        advance()

        assertNull("error: ${viewModel.uiState.value.error}", viewModel.uiState.value.error)
        assertEquals("Sí, créalas", viewModel.uiState.value.followUpPrompt?.label)
    }

    @Test
    fun `a plain answer does not show the follow-up chip`() {
        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        assertNull(viewModel.uiState.value.followUpPrompt)
    }

    @Test
    fun `a shopping list without card shows the shopping follow-up chip`() {
        // Regresión: tras "continúa donde lo dejaste" la puerta de compra no se abre (el
        // mensaje del usuario no lleva palabras de comida), así que la lista terminaba sin
        // tarjeta y, si mencionaba "rutina" de pasada, con el chip de RUTINAS equivocado.
        fakeRepository.cannedReply = "Aquí tienes la lista de la compra: pollo, arroz y tomates."

        viewModel.onInputChange("continúa donde lo dejaste")
        viewModel.onSendMessage()
        advance()

        val chip = viewModel.uiState.value.followUpPrompt
        assertEquals(FollowUpTarget.SHOPPING, chip?.target)
        assertEquals("la puerta de compra no debe abrirse aún", 0, fakeRepository.extractShoppingCallCount)
    }

    @Test
    fun `pressing the shopping follow-up chip extracts shopping and not routines`() {
        fakeRepository.cannedReply = "Aquí tienes la lista de la compra: pollo, arroz y tomates."
        viewModel.onInputChange("continúa donde lo dejaste")
        viewModel.onSendMessage()
        advance()
        val chip = viewModel.uiState.value.followUpPrompt
        assertEquals(FollowUpTarget.SHOPPING, chip?.target)

        viewModel.onQuickPrompt(chip!!.prompt)
        advance()

        assertEquals("debe extraer la compra", 1, fakeRepository.extractShoppingCallCount)
        assertEquals("no debe extraer rutinas", 0, fakeRepository.extractRoutinesCallCount)
    }

    @Test
    fun `pressing the routines follow-up chip extracts routines and not shopping`() {
        fakeRepository.cannedReply = "Te propongo estas rutinas para casa: fregar y barrer a diario"
        viewModel.onInputChange("dame ideas para organizarme")
        viewModel.onSendMessage()
        advance()
        val chip = viewModel.uiState.value.followUpPrompt
        assertEquals(FollowUpTarget.ROUTINES, chip?.target)

        viewModel.onQuickPrompt(chip!!.prompt)
        advance()

        assertEquals("debe extraer rutinas", 1, fakeRepository.extractRoutinesCallCount)
        assertEquals("no debe extraer la compra", 0, fakeRepository.extractShoppingCallCount)
    }

    @Test
    fun `pressing a follow-up chip does not start a conversational turn`() {
        fakeRepository.cannedReply = "Te propongo estas rutinas para casa: fregar y barrer a diario"
        viewModel.onInputChange("dame ideas para organizarme")
        viewModel.onSendMessage()
        advance()
        val chip = viewModel.uiState.value.followUpPrompt
        assertEquals(FollowUpTarget.ROUTINES, chip?.target)
        val sendCountBefore = fakeRepository.sendMessageCallCount
        val messagesBefore = viewModel.uiState.value.chatSession.messages.size

        viewModel.onQuickPrompt(chip!!.prompt)
        advance()

        val state = viewModel.uiState.value
        // El chip NO manda otro turno al modelo (esa era la parte lenta).
        assertEquals("el chip no debe llamar a sendMessage", sendCountBefore, fakeRepository.sendMessageCallCount)
        // Añade la confirmación del usuario y el "¡Voy!" sin pasar por el modelo.
        assertEquals(messagesBefore + 2, state.chatSession.messages.size)
        // La conversación nativa se recrea porque no vio este intercambio.
        assertTrue("debe recrear la conversación", fakeRepository.resetSessionCallCount > 0)
    }

    @Test
    fun `a successful shopping chip attaches the card to the confirmation message`() {
        fakeRepository.cannedReply = "Aquí tienes la lista de la compra: pollo, arroz y tomates."
        fakeRepository.shoppingResult =
            """{"shopping_list":[{"name":"Pollo","quantity":1,"unit":"unidad","category":"Carnes y Pescados"}]}"""
        viewModel.onInputChange("continúa donde lo dejaste")
        viewModel.onSendMessage()
        advance()
        val chip = viewModel.uiState.value.followUpPrompt
        assertEquals(FollowUpTarget.SHOPPING, chip?.target)

        viewModel.onQuickPrompt(chip!!.prompt)
        advance()

        val state = viewModel.uiState.value
        assertNull("no debe haber error: ${state.error}", state.error)
        // La tarjeta cuelga del último mensaje (la confirmación "¡Voy!").
        val lastMessage = state.chatSession.messages.last()
        val card = state.shoppingSuggestions[lastMessage.id]
        assertEquals(1, card?.size)
        assertEquals("Pollo", card?.first()?.name)
        // Ya hay tarjeta: el chip no reaparece.
        assertNull(state.followUpPrompt)
    }

    @Test
    fun `a chip whose extraction finds nothing re-posts the chip with an error`() {
        fakeRepository.cannedReply = "Aquí tienes la lista de la compra: pollo, arroz y tomates."
        fakeRepository.shoppingResult = "" // la extracción no devuelve JSON aprovechable
        viewModel.onInputChange("continúa donde lo dejaste")
        viewModel.onSendMessage()
        advance()
        val chip = viewModel.uiState.value.followUpPrompt
        assertEquals(FollowUpTarget.SHOPPING, chip?.target)

        viewModel.onQuickPrompt(chip!!.prompt)
        advance()

        val state = viewModel.uiState.value
        // El chip vuelve para poder reintentar, y se avisa del fallo.
        assertEquals(FollowUpTarget.SHOPPING, state.followUpPrompt?.target)
        assertTrue("debe avisar del fallo", state.error != null)
    }

    @Test
    fun `onCompactContext summarizes and keeps the recent messages`() {
        // 3 intercambios = 6 mensajes.
        repeat(3) {
            viewModel.onInputChange("mensaje $it")
            viewModel.onSendMessage()
            advance()
        }
        fakeRepository.summaryResult = "- El usuario pidió organizarse\n- Se propusieron rutinas"
        val resetsBefore = fakeRepository.resetSessionCallCount

        viewModel.onCompactContext()
        advance()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepository.summarizeCallCount)
        assertEquals(
            "- El usuario pidió organizarse\n- Se propusieron rutinas",
            state.chatSession.contextSummary
        )
        assertEquals("resume todo menos los 4 recientes", 2, state.chatSession.summarizedUpTo)
        assertFalse(state.isCompacting)
        assertNull("compactar no es un error: ${state.error}", state.error)
        assertTrue("debe recrear la conversación nativa", fakeRepository.resetSessionCallCount > resetsBefore)
    }

    @Test
    fun `onCompactContext with a blank summary sets an error and leaves the session intact`() {
        repeat(3) {
            viewModel.onInputChange("mensaje $it")
            viewModel.onSendMessage()
            advance()
        }
        fakeRepository.summaryResult = "" // el modelo no pudo resumir
        val messagesBefore = viewModel.uiState.value.chatSession.messages.size

        viewModel.onCompactContext()
        advance()

        val state = viewModel.uiState.value
        assertTrue("debe avisar del fallo", state.error != null)
        assertEquals("", state.chatSession.contextSummary)
        assertEquals(0, state.chatSession.summarizedUpTo)
        assertEquals(messagesBefore, state.chatSession.messages.size)
        assertFalse(state.isCompacting)
    }

    @Test
    fun `onCompactContext does nothing on a short conversation`() {
        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance() // 2 mensajes, por debajo del mínimo para compactar

        viewModel.onCompactContext()
        advance()

        assertEquals(0, fakeRepository.summarizeCallCount)
    }

    @Test
    fun `first exchange generates a session title`() {
        fakeRepository.generatedTitle = "Plan de limpieza semanal"

        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        assertNull("error: ${viewModel.uiState.value.error}", viewModel.uiState.value.error)
        assertEquals("títulos generados", 1, fakeRepository.generateTitleCallCount)
        assertEquals("Plan de limpieza semanal", viewModel.uiState.value.chatSession.title)

        // El segundo intercambio no vuelve a generar título.
        viewModel.onInputChange("¿Y algo más?")
        viewModel.onSendMessage()
        advance()
        assertEquals(1, fakeRepository.generateTitleCallCount)
    }

    @Test
    fun `onStopGeneration keeps partial text and stops generating`() {
        fakeRepository.hangAfterFirstChunk = true

        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()

        assertTrue(viewModel.uiState.value.isGenerating)

        viewModel.onStopGeneration()
        advance()

        val state = viewModel.uiState.value
        assertFalse("isGenerating should be false after stop", state.isGenerating)
        assertNull("Stopping is not an error", state.error)
        assertEquals("Respuesta parcial", state.chatSession.messages.last().content)
        // El texto parcial queda persistido al parar.
        assertEquals(
            "Respuesta parcial",
            fakeRepository.savedSessions.last().messages.last().content
        )
    }

    @Test
    fun `onStopGeneration without active generation does nothing`() {
        viewModel.onStopGeneration()
        advance()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals(0, fakeRepository.sendMessageCallCount)
    }

    @Test
    fun `new chat right after stopping a generation starts an empty chat`() {
        // Regresión: parar la generación y pulsar "+" seguido dejaba la pantalla sin hacer
        // nada (en el dispositivo, recrear la conversación bajo una inferencia aún viva
        // colgaba el engine). Ahora parada y nuevo chat se serializan con cancelAndJoin.
        fakeRepository.hangAfterFirstChunk = true

        viewModel.onInputChange("Hola")
        viewModel.onSendMessage()
        advance()
        assertTrue(viewModel.uiState.value.isGenerating)

        // Sin advance entre medias: el usuario pulsa parar y "+" del tirón.
        viewModel.onStopGeneration()
        viewModel.onNewChat()
        advance()

        val state = viewModel.uiState.value
        assertFalse("no debe seguir generando", state.isGenerating)
        assertNull("parar y abrir un chat nuevo no es un error", state.error)
        assertTrue("el chat nuevo debe quedar vacío", state.chatSession.messages.isEmpty())
        assertTrue("debe recrearse la sesión del engine", fakeRepository.resetSessionCallCount > 0)
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
