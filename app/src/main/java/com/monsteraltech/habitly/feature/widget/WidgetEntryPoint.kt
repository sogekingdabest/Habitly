package com.monsteraltech.habitly.feature.widget

import com.monsteraltech.habitly.feature.widget.domain.BuildWidgetSnapshotUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Puente para obtener dependencias de Hilt desde el widget (que no es un componente
 * inyectable). Sigue el mismo enfoque "sin hilt-work" que el resto de la app.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun buildWidgetSnapshotUseCase(): BuildWidgetSnapshotUseCase
}
