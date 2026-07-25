package com.monsteraltech.habitly.feature.widget.di

import com.monsteraltech.habitly.feature.widget.GlanceWidgetRefresher
import com.monsteraltech.habitly.feature.widget.domain.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}
