package com.monsteraltech.habitly.feature.aiassistant.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [AiChatSessionEntity::class], version = 1, exportSchema = false)
@TypeConverters(AiConverters::class)
abstract class AiAssistantDatabase : RoomDatabase() {
    abstract val aiChatDao: AiChatDao
}
