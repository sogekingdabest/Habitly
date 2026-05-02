package com.monsteraltech.habitly.feature.aiassistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatSessionTest {

    @Test
    fun `new session has default values`() {
        val session = AiChatSession()

        assertFalse(session.id.isEmpty())
        assertEquals("Nueva conversación", session.title)
        assertTrue(session.systemPrompt.isEmpty())
        assertEquals(AvailableAiModels.Gemma4_E2B_IT.id, session.modelId)
        assertTrue(session.messages.isEmpty())
    }

    @Test
    fun `addUserMessage adds message with User role`() {
        val session = AiChatSession()

        val updated = session.addUserMessage("Hola")

        assertEquals(1, updated.messages.size)
        assertEquals(MessageRole.User, updated.messages[0].role)
        assertEquals("Hola", updated.messages[0].content)
    }

    @Test
    fun `addUserMessage updates title on first message`() {
        val session = AiChatSession()

        val updated = session.addUserMessage("Hola mundo")

        assertEquals("Hola mundo", updated.title)
    }

    @Test
    fun `addUserMessage title is truncated at 30 chars with ellipsis`() {
        val session = AiChatSession()

        val updated = session.addUserMessage("Esta es una pregunta muy larga que excede el limite")

        assertTrue(updated.title.length > 30)
        assertTrue(updated.title.endsWith("..."))
    }

    @Test
    fun `addUserMessage title is not truncated when under 30 chars`() {
        val session = AiChatSession()

        val updated = session.addUserMessage("Hola mundo")

        assertEquals("Hola mundo", updated.title)
    }

    @Test
    fun `addUserMessage does not change title on subsequent messages`() {
        val session = AiChatSession().addUserMessage("Primera")

        val updated = session.addUserMessage("Segunda pregunta muy larga que no deberia cambiar el titulo")

        assertEquals("Primera", updated.title)
    }

    @Test
    fun `addUserMessage updates timestamp`() {
        val session = AiChatSession()
        Thread.sleep(10)

        val updated = session.addUserMessage("Hola")

        assertTrue(updated.timestamp > session.timestamp)
    }

    @Test
    fun `addAssistantMessage adds message with Assistant role`() {
        val session = AiChatSession()

        val updated = session.addAssistantMessage("Respuesta")

        assertEquals(1, updated.messages.size)
        assertEquals(MessageRole.Assistant, updated.messages[0].role)
        assertEquals("Respuesta", updated.messages[0].content)
    }

    @Test
    fun `updateLastAssistantMessage updates content when last message is Assistant`() {
        val session = AiChatSession()
            .addUserMessage("Pregunta")
            .addAssistantMessage("Parcial")

        val updated = session.updateLastAssistantMessage("Respuesta completa")

        assertEquals(2, updated.messages.size)
        assertEquals("Respuesta completa", updated.messages[1].content)
        assertEquals(MessageRole.Assistant, updated.messages[1].role)
    }

    @Test
    fun `updateLastAssistantMessage returns same session when last message is not Assistant`() {
        val session = AiChatSession().addUserMessage("Pregunta")

        val updated = session.updateLastAssistantMessage("No deberia pasar")

        assertEquals(session, updated)
    }

    @Test
    fun `updateLastAssistantMessage returns same session when no messages`() {
        val session = AiChatSession()

        val updated = session.updateLastAssistantMessage("No deberia pasar")

        assertEquals(session, updated)
    }

    @Test
    fun `addUserMessage preserves systemPrompt`() {
        val session = AiChatSession(systemPrompt = "Eres un asistente experto")

        val updated = session.addUserMessage("Hola")

        assertEquals("Eres un asistente experto", updated.systemPrompt)
    }

    @Test
    fun `multiple messages accumulate correctly`() {
        val session = AiChatSession()
            .addUserMessage("Pregunta 1")
            .addAssistantMessage("Respuesta 1")
            .addUserMessage("Pregunta 2")
            .addAssistantMessage("Respuesta 2")

        assertEquals(4, session.messages.size)
        assertEquals(MessageRole.User, session.messages[0].role)
        assertEquals(MessageRole.Assistant, session.messages[1].role)
        assertEquals(MessageRole.User, session.messages[2].role)
        assertEquals(MessageRole.Assistant, session.messages[3].role)
    }

    @Test
    fun `sessions with different content have different ids`() {
        val session1 = AiChatSession()
        val session2 = AiChatSession()

        assertNotEquals(session1.id, session2.id)
    }
}
