package com.monsteraltech.habitly.feature.routines.di

import com.monsteraltech.habitly.feature.routines.data.repository.RoutinesRepositoryImpl
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RoutinesModule {
    @Binds
    @Singleton
    abstract fun bindRoutinesRepository(
        impl: RoutinesRepositoryImpl
    ): RoutinesRepository
}
