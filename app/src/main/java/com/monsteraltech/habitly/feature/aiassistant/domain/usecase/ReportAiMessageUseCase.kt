package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository
import javax.inject.Inject

/**
 * Reporta una respuesta del asistente como ofensiva o inapropiada (requisito de la política
 * de contenido generado por IA de Google Play). Solo aplican mensajes del asistente con
 * contenido; se envía el texto completo, incluido el bloque estructurado oculto, porque es
 * lo que el modelo generó realmente.
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
