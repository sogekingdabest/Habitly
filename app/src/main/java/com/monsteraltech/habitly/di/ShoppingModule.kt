package com.monsteraltech.habitly.di

import com.monsteraltech.habitly.feature.shopping.data.repository.PantryRepositoryImpl
import com.monsteraltech.habitly.feature.shopping.data.repository.ShoppingRepositoryImpl
import com.monsteraltech.habitly.feature.shopping.domain.repository.PantryRepository
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShoppingModule {

    @Binds
    @Singleton
    abstract fun bindShoppingRepository(
        shoppingRepositoryImpl: ShoppingRepositoryImpl
    ): ShoppingRepository

    @Binds
    @Singleton
    abstract fun bindPantryRepository(
        pantryRepositoryImpl: PantryRepositoryImpl
    ): PantryRepository
}
