package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiMessage

@Entity(
    tableName = "ai_chat_sessions",
    // The history lives in a local database shared by every account on the device: with no owner,
    // each account could see the others' conversations. Indexed so the per-user queries do not
    // scan the whole table.
    indices = [Index("userId")]
)
data class AiChatSessionEntity(
    @PrimaryKey val id: String,
    /** Firebase UID of the session's owner. Isolates history between local accounts. */
    val userId: String,
    val title: String,
    val systemPrompt: String,
    val modelId: String,
    val timestamp: Long,
    val messages: List<AiMessage>,
    // Columns added in v2 (context compaction). The migration creates them with DEFAULT '' / 0 for
    // existing rows; the entity deliberately declares no @ColumnInfo(defaultValue), so Room skips
    // comparing defaults when validating the migration.
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
