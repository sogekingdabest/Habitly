package com.monsteraltech.habitly.feature.aiassistant.domain.repository

/**
 * Reports of assistant answers the user flagged as offensive or inappropriate. Google Play's
 * AI-generated content policy requires them to be reportable from inside the app.
 */
interface AiReportRepository {
    /** Sends [content] — the reported answer — and the [modelId] that produced it, for review. */
    suspend fun reportAssistantMessage(content: String, modelId: String): Result<Unit>
}
