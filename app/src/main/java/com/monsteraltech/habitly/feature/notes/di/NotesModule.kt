package com.monsteraltech.habitly.feature.notes.di

import com.monsteraltech.habitly.feature.notes.data.repository.NotesRepositoryImpl
import com.monsteraltech.habitly.feature.notes.domain.repository.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotesModule {
    @Binds
    @Singleton
    abstract fun bindNotesRepository(
        impl: NotesRepositoryImpl
    ): NotesRepository
}
