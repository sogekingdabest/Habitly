package com.monsteraltech.habitly.feature.aiassistant.domain.repository

/**
 * Reportes de respuestas del asistente marcadas por el usuario como ofensivas o inapropiadas.
 * La política de contenido generado por IA de Google Play exige poder reportarlas desde la app.
 */
interface AiReportRepository {
    /** Envía [content] (la respuesta reportada) y el [modelId] que la generó para su revisión. */
    suspend fun reportAssistantMessage(content: String, modelId: String): Result<Unit>
}
