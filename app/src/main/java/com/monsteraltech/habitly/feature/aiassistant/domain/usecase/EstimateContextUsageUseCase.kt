package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import javax.inject.Inject

/**
 * Estimates how much of the model's context budget the session takes up, in `[0f, 1f]`, so a banner
 * can suggest compacting before prefill starts failing. It counts characters because on-device
 * models expose no cheap token counter: roughly [CHARS_PER_TOKEN] characters per token in Spanish,
 * minus whatever [responseReserveFor] holds back for the answer.
 *
 * Already-compacted text is not double counted: the first [AiChatSession.summarizedUpTo] messages
 * are covered by [AiChatSession.contextSummary], which *is* counted since it rides in the system
 * prompt.
 */
class EstimateContextUsageUseCase @Inject constructor() {

    operator fun invoke(session: AiChatSession, maxTokens: Int): Float {
        val historyChars = session.messages
            .drop(session.summarizedUpTo)
            .sumOf { it.content.length }
        val totalChars = session.systemPrompt.length + session.contextSummary.length + historyChars
        val estimatedTokens = totalChars / CHARS_PER_TOKEN
        val budget = (maxTokens - responseReserveFor(maxTokens)).coerceAtLeast(1)
        return (estimatedTokens / budget).coerceIn(0f, 1f)
    }

    companion object {
        /** Characters per token, approximated for Spanish. */
        const val CHARS_PER_TOKEN = 3.5f

        /** Ceiling on tokens reserved for the answer. */
        const val MAX_RESPONSE_RESERVE_TOKENS = 1000

        /** Largest share of the window that may be reserved for the answer. */
        const val RESPONSE_RESERVE_FRACTION = 4

        /**
         * Tokens left free for the answer. Proportional on purpose: as a flat 1000 it ate half the
         * window on a 2048-token model, and with the system prompt already taking 650 the session
         * read 100% after the first exchange. Never more than a quarter of the budget.
         */
        fun responseReserveFor(maxTokens: Int): Int =
            minOf(MAX_RESPONSE_RESERVE_TOKENS, maxTokens / RESPONSE_RESERVE_FRACTION)
    }
}
