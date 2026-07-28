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
         * v1 → v2: context-compaction columns. Added NOT NULL with DEFAULT, which SQLite requires
         * when adding NOT NULL columns to a table that already has rows. The entity declares no
         * defaults, so Room skips comparing them when validating the migration.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN contextSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN summarizedUpTo INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 → v3: per-account history isolation. Adds the `userId` column and its index.
         *
         * Existing rows were written to a shared table with no attributable owner — the very bug
         * this migration fixes — so they are **deleted**: pre-beta, losing unattributable local
         * history beats risking one account's conversations showing up in another. The index is
         * named exactly as Room generates it (`index_<table>_<column>`) so runtime schema
         * validation finds no discrepancy.
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
