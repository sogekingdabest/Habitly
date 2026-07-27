package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage

@Entity(
    tableName = "ai_chat_sessions",
    indices = [Index("userId")]
)
data class AiChatSessionEntity(
    @PrimaryKey val id: String,
    /** Firebase Auth UID owning this session. */
    val userId: String,
    val title: String,
    val systemPrompt: String,
    val modelId: String,
    val timestamp: Long,
    val messages: List<AiMessage>,
    /** Context compaction summary (v2 schema). */
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
        fun fromDomain(session: AiChatSession, userId: String): AiChatSessionEntity {
            return AiChatSessionEntity(
                id = session.id,
                userId = userId,
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
