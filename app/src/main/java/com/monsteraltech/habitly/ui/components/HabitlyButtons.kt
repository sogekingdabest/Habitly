package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.habitly

private val ButtonShape = RoundedCornerShape(18.dp)

/**
 * Botón primario de Habitly: verde de marca, muy redondeado y con **sombra de color**
 * (no la elevación gris de Material). Texto en Baloo 2 sobre crema.
 *
 * Es un pulsable accesible (rol de botón, altura mínima de 52dp, ripple recortado a la
 * forma, estado deshabilitado atenuado).
 */
@Composable
fun HabitlyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary
    val interactive = enabled && !loading
    Row(
        modifier = modifier
            .shadow(
                elevation = if (interactive) 14.dp else 0.dp,
                shape = ButtonShape,
                spotColor = container,
                ambientColor = container,
            )
            .clip(ButtonShape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = interactive, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = content,
                strokeWidth = 2.5.dp,
            )
        } else {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Botón secundario: papel crema con borde verde y texto verde. Para la acción de
 * apoyo junto al primario ("Marcar todo").
 */
@Composable
fun HabitlySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val habitly = MaterialTheme.habitly
    Row(
        modifier = modifier
            .clip(ButtonShape)
            .background(habitly.card)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Botón de texto: solo etiqueta en verde de acento, para acciones terciarias. */
@Composable
fun HabitlyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.habitly.accentText,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
