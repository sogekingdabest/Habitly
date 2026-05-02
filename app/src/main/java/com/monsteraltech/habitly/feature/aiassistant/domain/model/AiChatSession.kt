package com.monsteraltech.habitly.feature.aiassistant.domain.model

import java.util.UUID

data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nueva conversación",
    val systemPrompt: String = "",
    val modelId: String = AvailableAiModels.Gemma4_E2B_IT.id,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<AiMessage> = emptyList()
) {
    fun addUserMessage(content: String): AiChatSession {
        val newTitle = if (messages.isEmpty() && title == "Nueva conversación") {
            content.take(30).let { if (content.length > 30) "$it..." else it }
        } else {
            title
        }
        return copy(
            title = newTitle,
            messages = messages + AiMessage(role = MessageRole.User, content = content),
            timestamp = System.currentTimeMillis()
        )
    }

    fun addAssistantMessage(content: String): AiChatSession {
        return copy(
            messages = messages + AiMessage(role = MessageRole.Assistant, content = content),
            timestamp = System.currentTimeMillis()
        )
    }

    fun updateLastAssistantMessage(newContent: String): AiChatSession {
        val lastMsg = messages.lastOrNull()
        if (lastMsg?.role == MessageRole.Assistant) {
            val updatedMessages = messages.dropLast(1) + lastMsg.copy(content = newContent)
            return copy(messages = updatedMessages)
        }
        return this
    }
}

