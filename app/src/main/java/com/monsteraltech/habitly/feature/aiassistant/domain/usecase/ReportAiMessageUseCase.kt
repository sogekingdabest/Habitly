package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository
import javax.inject.Inject

/**
 * Reports an assistant answer as offensive or inappropriate, as Google Play's AI-generated content
 * policy requires. Only assistant messages with content qualify, and the full text is sent —
 * including the hidden structured block — because that is what the model actually generated.
 */
class ReportAiMessageUseCase @Inject constructor(
    private val reportRepository: AiReportRepository
) {
    suspend operator fun invoke(message: AiMessage, modelId: String): Result<Unit> {
        if (message.role !is MessageRole.Assistant) {
            return Result.failure(IllegalArgumentException("Solo se pueden reportar respuestas del asistente"))
        }
        if (message.content.isBlank()) {
            return Result.failure(IllegalArgumentException("La respuesta está vacía"))
        }
        return reportRepository.reportAssistantMessage(message.content, modelId)
    }
}
