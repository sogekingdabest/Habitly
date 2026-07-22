package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import javax.inject.Inject

/**
 * Estima cuánto del presupuesto de contexto del modelo lleva ocupado la sesión, en `[0f, 1f]`.
 * Sirve para avisar (banner) de que conviene compactar antes de que el prefill empiece a
 * fallar. Es una estimación por caracteres (los modelos on-device no exponen un contador de
 * tokens barato): ~[CHARS_PER_TOKEN] caracteres por token en español, reservando
 * [RESPONSE_RESERVE_TOKENS] tokens para la respuesta.
 *
 * No cuenta lo ya compactado: [AiChatSession.summarizedUpTo] mensajes del principio están
 * cubiertos por [AiChatSession.contextSummary], que sí se cuenta (viaja en el system prompt).
 */
class EstimateContextUsageUseCase @Inject constructor() {

    operator fun invoke(session: AiChatSession, maxTokens: Int): Float {
        val historyChars = session.messages
            .drop(session.summarizedUpTo)
            .sumOf { it.content.length }
        val totalChars = session.systemPrompt.length + session.contextSummary.length + historyChars
        val estimatedTokens = totalChars / CHARS_PER_TOKEN
        val budget = (maxTokens - RESPONSE_RESERVE_TOKENS).coerceAtLeast(1)
        return (estimatedTokens / budget).coerceIn(0f, 1f)
    }

    companion object {
        /** Caracteres por token (aproximación para español). */
        const val CHARS_PER_TOKEN = 3.5f

        /** Tokens que se dejan libres para la respuesta al medir el presupuesto. */
        const val RESPONSE_RESERVE_TOKENS = 1000
    }
}
