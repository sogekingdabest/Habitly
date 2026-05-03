package com.monsteraltech.habitly.feature.routines.di

import android.content.Context
import com.monsteraltech.habitly.feature.routines.data.cache.RoutinesCacheManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoutinesCacheModule {

    @Provides
    @Singleton
    fun provideRoutinesCacheManager(
        @ApplicationContext context: Context
    ): RoutinesCacheManager {
        return RoutinesCacheManager(context)
    }
}
