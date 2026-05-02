package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole

class AiConverters {
    private val gson = Gson()

    // We need a helper class because Gson struggles with sealed classes directly if not configured,
    // but since MessageRole only has objects, we can store it as String.

    data class AiMessageDto(
        val id: String,
        val roleStr: String,
        val content: String,
        val timestamp: Long
    )

    @TypeConverter
    fun fromMessageList(messages: List<AiMessage>): String {
        val dtos = messages.map {
            AiMessageDto(
                id = it.id,
                roleStr = when (it.role) {
                    is MessageRole.System -> "system"
                    is MessageRole.User -> "user"
                    is MessageRole.Assistant -> "assistant"
                },
                content = it.content,
                timestamp = it.timestamp
            )
        }
        return gson.toJson(dtos)
    }

    @TypeConverter
    fun toMessageList(data: String): List<AiMessage> {
        if (data.isBlank()) return emptyList()
        val listType = object : TypeToken<List<AiMessageDto>>() {}.type
        val dtos: List<AiMessageDto> = gson.fromJson(data, listType) ?: return emptyList()
        
        return dtos.map {
            AiMessage(
                id = it.id,
                role = when (it.roleStr) {
                    "system" -> MessageRole.System
                    "assistant" -> MessageRole.Assistant
                    else -> MessageRole.User
                },
                content = it.content,
                timestamp = it.timestamp
            )
        }
    }
}
