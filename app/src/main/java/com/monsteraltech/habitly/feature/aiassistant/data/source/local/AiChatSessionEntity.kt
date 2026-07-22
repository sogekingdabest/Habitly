package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage

@Entity(tableName = "ai_chat_sessions")
data class AiChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val systemPrompt: String,
    val modelId: String,
    val timestamp: Long,
    val messages: List<AiMessage>,
    // Columnas añadidas en la v2 (compactación de contexto). La migración las crea con
    // DEFAULT '' / 0 para las filas existentes; la entidad no declara @ColumnInfo(defaultValue)
    // a propósito, así Room omite la comparación de defaults al validar la migración.
    val contextSummary: String = "",
    val summarizedUpTo: Int = 0
) {
    fun toDomain(): AiChatSession {
        return AiChatSession(
            id = id,
            title = title,
            systemPrompt = systemPrompt,
            modelId = modelId,
            timestamp = timestamp,
            messages = messages,
            contextSummary = contextSummary,
            summarizedUpTo = summarizedUpTo
        )
    }

    companion object {
        fun fromDomain(session: AiChatSession): AiChatSessionEntity {
            return AiChatSessionEntity(
                id = session.id,
                title = session.title,
                systemPrompt = session.systemPrompt,
                modelId = session.modelId,
                timestamp = session.timestamp,
                messages = session.messages,
                contextSummary = session.contextSummary,
                summarizedUpTo = session.summarizedUpTo
            )
        }
    }
}
