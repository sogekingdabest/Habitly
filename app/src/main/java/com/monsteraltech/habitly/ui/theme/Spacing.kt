package com.monsteraltech.habitly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado de Habitly, en pasos de 4dp.
 *
 * Material 3 no define tokens de espaciado, así que cada pantalla acababa
 * improvisando sus propios `padding`. Usar esta escala en vez de valores sueltos
 * es lo que da ritmo vertical constante entre pantallas.
 *
 * Uso: `Modifier.padding(MaterialTheme.spacing.md)`
 */
@Immutable
data class Spacing(
    /** 4dp — separación entre un icono y su etiqueta. */
    val xs: Dp = 4.dp,
    /** 8dp — separación entre elementos de una misma fila o grupo. */
    val sm: Dp = 8.dp,
    /** 16dp — margen lateral de pantalla y separación entre tarjetas. */
    val md: Dp = 16.dp,
    /** 24dp — separación entre secciones dentro de una pantalla. */
    val lg: Dp = 24.dp,
    /** 32dp — respiro alrededor de bloques destacados o estados vacíos. */
    val xl: Dp = 32.dp,
    /** 48dp — separación de bloques principales en pantallas de onboarding. */
    val xxl: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Acceso a la escala de espaciado desde cualquier composable bajo `HabitlyTheme`. */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
