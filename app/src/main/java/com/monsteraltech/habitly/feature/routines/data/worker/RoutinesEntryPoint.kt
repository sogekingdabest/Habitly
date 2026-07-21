package com.monsteraltech.habitly.feature.routines.data.worker

import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Puente para obtener dependencias de Hilt desde el worker de recordatorios, que WorkManager
 * instancia por su cuenta. Sigue el mismo enfoque "sin hilt-work" que el widget.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RoutinesEntryPoint {
    fun routinesRepository(): RoutinesRepository
}
