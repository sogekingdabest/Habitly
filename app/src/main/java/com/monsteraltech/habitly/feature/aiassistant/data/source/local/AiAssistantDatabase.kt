package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AiChatSessionEntity::class], version = 3, exportSchema = false)
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

        /**
         * v2 → v3: aislamiento del historial por cuenta. Añade la columna `userId` y su índice.
         *
         * Las filas ya existentes se crearon en una tabla compartida sin dueño atribuible (el
         * bug que esta migración corrige), así que se ELIMINAN: en pre-beta es preferible perder
         * historial local no atribuible a arriesgarse a mostrar conversaciones de una cuenta a
         * otra. El índice se nombra igual que el que genera Room (`index_<tabla>_<columna>`)
         * para que la validación de esquema en tiempo de ejecución no detecte discrepancias.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM ai_chat_sessions")
                db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_chat_sessions_userId " +
                        "ON ai_chat_sessions (userId)"
                )
            }
        }
    }
}
