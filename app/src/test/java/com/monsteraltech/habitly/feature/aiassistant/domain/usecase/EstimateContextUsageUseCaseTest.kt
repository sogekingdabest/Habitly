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

    // Reserva proporcional. Viene de un fallo real: con el modelo ligero (ventana de 2048) la
    // primera respuesta ya marcaba 100%, porque la reserva era una constante de 1000 tokens
    // pensada para ventanas de 4096 y se comía media ventana.

    @Test
    fun `the response reserve never eats more than a quarter of the window`() {
        listOf(1024, 2048, 4096, 8192).forEach { maxTokens ->
            val reserve = EstimateContextUsageUseCase.responseReserveFor(maxTokens)
            assertTrue(
                "Con $maxTokens tokens la reserva ($reserve) pasa del cuarto de la ventana",
                reserve <= maxTokens / 4
            )
            assertTrue("La reserva no puede ser cero", reserve > 0)
        }
    }

    @Test
    fun `large windows keep the reserve they always had`() {
        // No debe cambiar el comportamiento de los Gemma, que ya iban bien.
        assertEquals(1000, EstimateContextUsageUseCase.responseReserveFor(4096))
        assertEquals(1000, EstimateContextUsageUseCase.responseReserveFor(8192))
    }

    @Test
    fun `one normal exchange does not fill the window`() {
        // Medidas reales del dispositivo: system prompt de 2271 caracteres, pregunta de 264 y
        // un menú semanal con lista de ingredientes de unos 4000.
        val session = AiChatSession(
            systemPrompt = "s".repeat(2271),
            messages = listOf(msg("u".repeat(264)), msg("a".repeat(4000)))
        )

        val usage = useCase(session, maxTokens = 4096)

        assertTrue("Un solo intercambio no debería agotar el contexto: $usage", usage < 0.9f)
    }

    @Test
    fun `a genuinely long conversation still warns`() {
        val messages = (1..8).flatMap { listOf(msg("u".repeat(300)), msg("a".repeat(3000))) }
        val session = AiChatSession(systemPrompt = "s".repeat(2271), messages = messages)

        assertEquals(1f, useCase(session, maxTokens = 4096), 0.001f)
    }

    private fun msg(content: String) = AiMessage(role = MessageRole.User, content = content)
}
