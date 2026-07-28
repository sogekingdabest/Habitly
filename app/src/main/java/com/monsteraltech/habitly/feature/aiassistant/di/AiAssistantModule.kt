package com.monsteraltech.habitly.feature.aiassistant.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.monsteraltech.habitly.feature.aiassistant.data.repository.AiAssistantRepositoryImpl
import com.monsteraltech.habitly.feature.aiassistant.data.repository.AiAccountDataCleaner
import com.monsteraltech.habitly.feature.aiassistant.data.repository.AiReportRepositoryImpl
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiAssistantDatabase
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiChatDao
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository
import com.monsteraltech.habitly.feature.login.domain.account.AccountDataCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiAssistantModule {

    @Binds
    abstract fun bindAiAssistantRepository(
        repositoryImpl: AiAssistantRepositoryImpl
    ): AiAssistantRepository

    @Binds
    abstract fun bindAiReportRepository(
        repositoryImpl: AiReportRepositoryImpl
    ): AiReportRepository

    /** Wipes the assistant's history and prefs on account deletion, not on logout. */
    @Binds
    @IntoSet
    abstract fun bindAiAccountDataCleaner(
        cleaner: AiAccountDataCleaner
    ): AccountDataCleaner

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
            )
                .addMigrations(
                    AiAssistantDatabase.MIGRATION_1_2,
                    AiAssistantDatabase.MIGRATION_2_3
                )
                .build()
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
