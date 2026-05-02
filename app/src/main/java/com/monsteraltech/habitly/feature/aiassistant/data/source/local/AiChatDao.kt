package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chat_sessions ORDER BY timestamp DESC")
    fun observeAllSessions(): Flow<List<AiChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: String): AiChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: AiChatSessionEntity): Long

    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId")
    fun deleteSession(sessionId: String): Int
}
