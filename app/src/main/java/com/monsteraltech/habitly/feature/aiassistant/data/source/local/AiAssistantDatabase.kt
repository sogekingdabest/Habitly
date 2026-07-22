package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AiChatSessionEntity::class], version = 2, exportSchema = false)
@TypeConverters(AiConverters::class)
abstract class AiAssistantDatabase : RoomDatabase() {
    abstract val aiChatDao: AiChatDao

    companion object {
        /**
         * v1 → v2: columnas de la compactación de contexto. Se añaden NOT NULL con DEFAULT
         * (obligatorio en SQLite al añadir columnas NOT NULL a una tabla con filas). La entidad
         * no declara defaults, así que Room omite su comparación al validar la migración.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN contextSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN summarizedUpTo INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
