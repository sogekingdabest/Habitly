package com.monsteraltech.habitly.di

import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.household.data.repository.HouseholdRepositoryImpl
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HouseholdModule {

    @Provides
    @Singleton
    fun provideHouseholdRepository(firestore: FirebaseFirestore): HouseholdRepository {
        return HouseholdRepositoryImpl(firestore)
    }
}
