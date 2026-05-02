package com.monsteraltech.habitly.feature.aiassistant.domain.model

sealed class MessageRole {
    data object System : MessageRole()
    data object User : MessageRole()
    data object Assistant : MessageRole()
}

data class AiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole = MessageRole.User,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
