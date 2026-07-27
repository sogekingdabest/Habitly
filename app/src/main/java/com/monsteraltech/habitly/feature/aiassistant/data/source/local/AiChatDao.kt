package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    // All queries are scoped by userId to isolate chat history per account.
    @Query("SELECT * FROM ai_chat_sessions WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeSessionsForUser(userId: String): Flow<List<AiChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :sessionId AND userId = :userId LIMIT 1")
    fun getSessionByIdForUser(sessionId: String, userId: String): AiChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: AiChatSessionEntity): Long

    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId AND userId = :userId")
    fun deleteSessionForUser(sessionId: String, userId: String): Int
}
