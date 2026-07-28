package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    // Every query is scoped by userId: the database is local and shared by all the accounts on the
    // device, so without this filter one account could see another's history.
    @Query("SELECT * FROM ai_chat_sessions WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeSessionsForUser(userId: String): Flow<List<AiChatSessionEntity>>

    // The userId is required alongside the id (defence in depth): it stops a session belonging to
    // another account being loaded by id.
    @Query("SELECT * FROM ai_chat_sessions WHERE id = :sessionId AND userId = :userId LIMIT 1")
    fun getSessionByIdForUser(sessionId: String, userId: String): AiChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: AiChatSessionEntity): Long

    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId AND userId = :userId")
    fun deleteSessionForUser(sessionId: String, userId: String): Int
}
