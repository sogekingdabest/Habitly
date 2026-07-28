package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    // Todas las consultas se acotan por userId: la BD es local y la comparten todas las
    // cuentas del dispositivo, así que sin este filtro una cuenta veía el historial de otra.
    @Query("SELECT * FROM ai_chat_sessions WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeSessionsForUser(userId: String): Flow<List<AiChatSessionEntity>>

    // Se exige el userId además del id (defensa en profundidad): impide cargar por id una
    // sesión que pertenezca a otra cuenta.
    @Query("SELECT * FROM ai_chat_sessions WHERE id = :sessionId AND userId = :userId LIMIT 1")
    fun getSessionByIdForUser(sessionId: String, userId: String): AiChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: AiChatSessionEntity): Long

    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId AND userId = :userId")
    fun deleteSessionForUser(sessionId: String, userId: String): Int
}
