package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Fila deslizable de Habitly.
 *
 * Con una mano ocupada —el carro del súper, un trapo, un niño— deslizar es más rápido y más
 * certero que acertar un icono de 20 dp. Deslizar **hacia la derecha** ejecuta la acción
 * principal (tachar, completar) y la fila vuelve a su sitio; deslizar **hacia la izquierda**
 * borra.
 *
 * El gesto nunca es la única vía: acompáñalo siempre de [swipeRowSemantics] en la fila
 * interior para que TalkBack ofrezca lo mismo sin gesto.
 *
 * @param dismissOnDelete si la fila debe quedarse fuera de pantalla tras el gesto de borrar.
 *   Ponlo a `false` cuando el borrado abra un diálogo de confirmación: la fila sigue ahí
 *   hasta que el usuario confirme.
 */
@Composable
fun HabitlySwipeRow(
    onPrimaryAction: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector = Icons.Outlined.Check,
    dismissOnDelete: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection, primaryIcon) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrimaryAction()
                    // La fila no desaparece, solo cambia de estado: vuelve a su sitio.
                    scope.launch { state.reset() }
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    if (!dismissOnDelete) scope.launch { state.reset() }
                }

                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        content = content,
    )
}

/**
 * Acciones equivalentes al gesto para los lectores de pantalla.
 *
 * Debe aplicarse **al mismo nodo** que lleva el `toggleable`/`clickable` de la fila: es el
 * que TalkBack enfoca, y las acciones personalizadas solo se ofrecen sobre el nodo enfocado.
 */
fun Modifier.swipeRowSemantics(
    primaryLabel: String,
    onPrimaryAction: () -> Unit,
    deleteLabel: String,
    onDelete: () -> Unit,
): Modifier = semantics {
    customActions = listOf(
        CustomAccessibilityAction(primaryLabel) { onPrimaryAction(); true },
        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
    )
}

/** Fondo que asoma al deslizar: verde con check a la derecha, rojo con papelera a la izquierda. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, primaryIcon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    val (background, tint, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            SwipeBackgroundSpec(scheme.primaryContainer, scheme.onPrimaryContainer, primaryIcon, Alignment.CenterStart)

        SwipeToDismissBoxValue.EndToStart ->
            SwipeBackgroundSpec(scheme.errorContainer, scheme.onErrorContainer, Icons.Outlined.Delete, Alignment.CenterEnd)

        SwipeToDismissBoxValue.Settled ->
            SwipeBackgroundSpec(Color.Transparent, Color.Transparent, primaryIcon, Alignment.CenterStart)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

private data class SwipeBackgroundSpec(
    val background: Color,
    val tint: Color,
    val icon: ImageVector,
    val alignment: Alignment,
)
