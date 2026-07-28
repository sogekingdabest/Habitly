package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * An item the AI assistant proposes adding to the shopping list, extracted from the structured
 * block the model appends to its answers.
 */
data class AiShoppingSuggestion(
    val name: String,
    val quantity: Int = 1,
    val unit: String = "unidad",
    val category: String = ""
)
