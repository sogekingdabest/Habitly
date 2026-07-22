package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateContextUsageUseCaseTest {

    private val useCase = EstimateContextUsageUseCase()

    @Test
    fun `empty session uses no context`() {
        assertEquals(0f, useCase(AiChatSession(), maxTokens = 4096), 0.0001f)
    }

    @Test
    fun `usage grows with message length`() {
        val short = AiChatSession(messages = listOf(msg("hola")))
        val long = AiChatSession(messages = listOf(msg("x".repeat(2000))))

        assertTrue(useCase(long, 4096) > useCase(short, 4096))
    }

    @Test
    fun `usage is capped at 1`() {
        val huge = AiChatSession(messages = listOf(msg("x".repeat(100_000))))

        assertEquals(1f, useCase(huge, 4096), 0.0001f)
    }

    @Test
    fun `summarized messages are not counted but the summary is`() {
        val messages = listOf(msg("a".repeat(1000)), msg("b".repeat(1000)), msg("c".repeat(1000)))
        val notCompacted = AiChatSession(messages = messages)
        val compacted = AiChatSession(
            messages = messages,
            contextSummary = "resumen corto",
            summarizedUpTo = 2
        )

        // Compactado cuenta solo el 3er mensaje + el resumen corto: menos que sin compactar.
        assertTrue(useCase(compacted, 4096) < useCase(notCompacted, 4096))
    }

    @Test
    fun `the system prompt counts toward usage`() {
        val withPrompt = AiChatSession(systemPrompt = "x".repeat(1000))

        assertTrue(useCase(withPrompt, 4096) > 0f)
    }

    private fun msg(content: String) = AiMessage(role = MessageRole.User, content = content)
}
