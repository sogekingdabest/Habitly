package com.monsteraltech.habitly.feature.aiassistant.domain.model

/** Stable identity of each suggestion chip. The text (label and prompt) is resolved in the
 *  presentation layer with `stringResource`, so it honours the language chosen in Settings. */
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
 * A suggestion offered as a chip above the chat input.
 *
 * It lives in domain because
 * [com.monsteraltech.habitly.feature.aiassistant.domain.usecase.GetContextualQuickPromptsUseCase]
 * builds them from the household's actual state. It carries only the [id] — plus, for the weekly
 * menu, the [memberCount] the prompt needs; the localised label and prompt come from the
 * presentation layer.
 */
data class AiQuickPrompt(
    val id: QuickPromptId,
    val memberCount: Int = 1
)
