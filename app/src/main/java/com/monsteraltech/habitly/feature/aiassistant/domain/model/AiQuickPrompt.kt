package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * Sugerencia que se ofrece como chip encima del input del chat.
 *
 * Vive en domain porque las construye
 * [com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase]
 * a partir del estado real de la casa; la capa de presentación solo las pinta.
 *
 * [label] y [prompt] van en español y sin traducir, igual que la personalidad del
 * asistente: el modelo on-device conversa en español.
 */
data class AiQuickPrompt(
    val label: String,
    val prompt: String
)
