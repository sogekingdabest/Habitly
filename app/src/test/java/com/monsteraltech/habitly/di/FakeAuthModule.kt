package com.monsteraltech.habitly.di

import com.monsteraltech.habitly.feature.login.data.repository.FakeAuthRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Reemplaza AuthModule en el grafo de Hilt durante los tests.
 *
 * @TestInstallIn desinstala AuthModule y lo sustituye por este módulo,
 * de forma que todos los tests de ese componente reciben FakeAuthRepository
 * sin necesidad de anotar cada test con @UninstallModules.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthModule::class]
)
abstract class FakeAuthModule {

    @Binds
    @Singleton
    abstract fun bindFakeAuthRepository(fake: FakeAuthRepository): AuthRepository
}
