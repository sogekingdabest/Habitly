package com.monsteraltech.habitly.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * Campo de texto con la piel Habitly: contenedor de papel crema, esquinas 16dp, borde
 * verde al enfocar y etiqueta flotante en verde de acento. Envuelve `OutlinedTextField`
 * de Material, así que conserva su comportamiento (foco, IME, accesibilidad, error).
 */
@Composable
fun HabitlyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val habitly = MaterialTheme.habitly
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = habitly.card,
            unfocusedContainerColor = habitly.card,
            disabledContainerColor = habitly.card,
            errorContainerColor = habitly.card,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = habitly.border,
            focusedLabelColor = habitly.accentText,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
