package com.monsteraltech.habitly.feature.routines.data.worker

import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge for pulling Hilt dependencies into the reminder worker, which WorkManager instantiates on
 * its own. Follows the same "no hilt-work" approach as the widget.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RoutinesEntryPoint {
    fun routinesRepository(): RoutinesRepository
}
