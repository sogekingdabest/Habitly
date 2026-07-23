package com.monsteraltech.habitly.feature.aiassistant.domain.model

/** Identidad estable de cada chip de sugerencia. El texto (label y prompt) se resuelve en la
 *  capa de presentación con `stringResource`, para que respete el idioma elegido en Ajustes. */
enum class QuickPromptId {
    WEEKLY_MENU,
    COOK_FROM_PANTRY,
    CLEANING_PLAN,
    RECIPES_FROM_LIST,
    WEEKLY_LIST,
    ORGANIZE_DAY,
    QUICK_DINNER,
    ROUTINE_IDEAS,
    CLEANING_TIPS
}

/**
 * Sugerencia que se ofrece como chip encima del input del chat.
 *
 * Vive en domain porque las construye
 * [com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase]
 * a partir del estado real de la casa. Solo lleva el [id] (y, para el menú semanal, el
 * [memberCount] que necesita el prompt); la etiqueta y el prompt localizados los pone la capa
 * de presentación con `stringResource`.
 */
data class AiQuickPrompt(
    val id: QuickPromptId,
    val memberCount: Int = 1
)
