package com.monsteraltech.habitly.feature.routines.data.worker

import com.monsteraltech.habitly.feature.household.domain.usecase.ObserveHouseholdUseCase
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.usecase.AdvanceRotationUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ToggleRoutineUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge for pulling Hilt dependencies into the workers, which WorkManager instantiates on its own.
 * Follows the same "no hilt-work" approach as the widget.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RoutinesEntryPoint {
    fun routinesRepository(): RoutinesRepository

    // Used by RoutineCompleteWorker, which completes a routine straight from the notification.
    fun toggleRoutineUseCase(): ToggleRoutineUseCase
    fun advanceRotationUseCase(): AdvanceRotationUseCase
    fun observeHouseholdUseCase(): ObserveHouseholdUseCase
}
