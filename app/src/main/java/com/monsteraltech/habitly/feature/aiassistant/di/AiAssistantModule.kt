package com.monsteraltech.habitly.feature.aiassistant.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.monsteraltech.habitly.feature.aiassistant.data.repository.AiAssistantRepositoryImpl
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiAssistantDatabase
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiChatDao
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiAssistantModule {

    @Binds
    abstract fun bindAiAssistantRepository(
        repositoryImpl: AiAssistantRepositoryImpl
    ): AiAssistantRepository

    companion object {
        @Provides
        @Singleton
        fun provideAiAssistantDatabase(
            @ApplicationContext context: Context
        ): AiAssistantDatabase {
            return Room.databaseBuilder(
                context,
                AiAssistantDatabase::class.java,
                "ai_assistant_db"
            ).build()
        }

        @Provides
        fun provideAiChatDao(
            database: AiAssistantDatabase
        ): AiChatDao {
            return database.aiChatDao
        }

        @Provides
        @Singleton
        fun provideSharedPreferences(
            @ApplicationContext context: Context
        ): SharedPreferences {
            return context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        }
    }
}
