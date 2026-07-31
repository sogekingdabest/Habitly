package com.monsteraltech.habitly.feature.widget

import com.monsteraltech.habitly.feature.widget.domain.BuildWidgetSnapshotUseCase
import com.monsteraltech.habitly.feature.widget.domain.WidgetActionsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge for pulling Hilt dependencies from the widget, which is not an injectable component. It
 * follows the same "no hilt-work" approach as the rest of the app.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun buildWidgetSnapshotUseCase(): BuildWidgetSnapshotUseCase
    fun widgetActionsUseCase(): WidgetActionsUseCase
}
