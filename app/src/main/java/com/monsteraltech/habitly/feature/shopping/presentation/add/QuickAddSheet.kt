package com.monsteraltech.habitly.feature.shopping.presentation.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import com.monsteraltech.habitly.feature.shopping.presentation.QuickAddState
import com.monsteraltech.habitly.feature.shopping.presentation.components.PantryHint
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyTextButton
import com.monsteraltech.habitly.ui.components.HabitlyTextField
import com.monsteraltech.habitly.ui.components.VoiceInputButton
import com.monsteraltech.habitly.ui.theme.habitly
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Espera antes de pedir el foco: la hoja entra animada y en el primer frame el campo aún no
 * está enganchado al árbol.
 */
private const val FOCUS_DELAY_MS = 150L

val CATEGORIES = listOf(
    "Frutas y Verduras",
    "Lácteos",
    "Carnes y Pescados",
    "Panadería",
    "Bebidas",
    "Limpieza",
    "Snacks",
    "Congelados",
    "Especias y Condimentos",
    "Otros"
)

val UNITS = listOf("unidad", "kg", "g", "L", "ml", "docena", "paquete")

/**
 * Alta rápida de producto.
 *
 * Sustituye a la antigua pantalla completa de seis campos. Para el 90 % de los casos ("pan")
 * solo hace falta el nombre, así que la hoja abre con ese único campo enfocado y el teclado
 * ya levantado, y **guardar no la cierra**: vacía el campo y devuelve el foco para poder
 * apuntar diez cosas del tirón. El resto de campos siguen ahí, plegados tras "Más opciones"
 * y con los mismos valores por defecto de antes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    state: QuickAddState,
    availableStores: List<String>,
    pantryQuantity: Int?,
    pantryUnit: String?,
    duplicate: ShoppingItem?,
    sheetState: SheetState,
    onNameChange: (String) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onUnitChange: (String) -> Unit,
    onStoreChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleOptions: () -> Unit,
    onSave: () -> Unit,
    onVoiceInput: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // El foco se pide al abrir y después de cada guardado, que es cuando el campo se vacía.
    // La espera es porque la hoja entra animada: en el primer frame el campo aún no está
    // enganchado y `requestFocus` lanzaría IllegalStateException.
    LaunchedEffect(state.savedCount) {
        delay(FOCUS_DELAY_MS)
        runCatching { focusRequester.requestFocus() }
    }

    // Cerrar de verdad: primero se retira la hoja con su animación y solo después se limpia
    // el estado. Si se limpiase antes, la hoja desaparecería de golpe.
    val dismissWithAnimation: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.addproduct_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            HabitlyTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.addproduct_name),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                // El dictado aquí **rellena** el formulario en vez de guardar: el usuario ve la
                // cantidad y la unidad reconocidas ("dos litros de leche" → 2 L) antes de dar de
                // alta. El botón desaparece si el dispositivo no tiene reconocedor.
                trailingIcon = { VoiceInputButton(onSpokenText = onVoiceInput) }
            )

            // Avisa de que ya lo tienes en casa antes de que lo compres otra vez.
            if (pantryQuantity != null && pantryUnit != null) {
                Spacer(Modifier.height(8.dp))
                PantryHint(quantity = pantryQuantity, unit = pantryUnit)
            }

            // …y de que ya está apuntado, que es la otra forma de comprarlo dos veces.
            if (duplicate != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (duplicate.isChecked) R.string.addproduct_duplicate_checked
                        else R.string.addproduct_duplicate_pending,
                        duplicate.name
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            MoreOptionsToggle(expanded = state.showMoreOptions, onClick = onToggleOptions)

            AnimatedVisibility(
                visible = state.showMoreOptions,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                MoreOptions(
                    state = state,
                    availableStores = availableStores,
                    onQuantityChange = onQuantityChange,
                    onUnitChange = onUnitChange,
                    onStoreChange = onStoreChange,
                    onCategoryChange = onCategoryChange,
                    onNotesChange = onNotesChange
                )
            }

            Spacer(Modifier.height(16.dp))

            HabitlyPrimaryButton(
                text = stringResource(R.string.addproduct_save_and_continue),
                onClick = onSave,
                enabled = state.canSave,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            // Recuento de lo que llevas apuntado sin salir: la única confirmación que hace
            // falta cuando la hoja no se cierra al guardar.
            if (state.savedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.addproduct_added_count,
                        state.savedCount,
                        state.savedCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.habitly.accentText,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(8.dp))

            HabitlyTextButton(
                text = stringResource(R.string.common_done),
                onClick = dismissWithAnimation,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MoreOptionsToggle(expanded: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HabitlyTextButton(
            text = stringResource(R.string.addproduct_more_options),
            onClick = onClick
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.habitly.accentText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MoreOptions(
    state: QuickAddState,
    availableStores: List<String>,
    onQuantityChange: (Int) -> Unit,
    onUnitChange: (String) -> Unit,
    onStoreChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HabitlyTextField(
                value = if (state.quantity == 0) "" else state.quantity.toString(),
                onValueChange = { onQuantityChange(it.toIntOrNull() ?: 0) },
                label = stringResource(R.string.addproduct_quantity),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OptionDropdown(
                label = stringResource(R.string.addproduct_unit),
                selected = state.unit,
                options = UNITS,
                onSelected = onUnitChange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        OptionDropdown(
            label = stringResource(R.string.addproduct_store),
            selected = state.store,
            options = availableStores,
            onSelected = onStoreChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OptionDropdown(
            label = stringResource(R.string.addproduct_category),
            selected = state.category,
            options = CATEGORIES,
            onSelected = onCategoryChange,
            modifier = Modifier.fillMaxWidth(),
            emptyOptionLabel = stringResource(R.string.addproduct_no_category)
        )

        Spacer(Modifier.height(12.dp))

        HabitlyTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            label = stringResource(R.string.addproduct_notes),
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Desplegable de una sola opción. [emptyOptionLabel] añade arriba la opción "sin valor"
 * (la categoría es opcional).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyOptionLabel: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val habitly = MaterialTheme.habitly

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.ifBlank { emptyOptionLabel.orEmpty() },
            onValueChange = { },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = habitly.card,
                unfocusedContainerColor = habitly.card,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = habitly.border,
                focusedLabelColor = habitly.accentText,
            )
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (emptyOptionLabel != null) {
                DropdownMenuItem(
                    text = { Text(emptyOptionLabel) },
                    onClick = {
                        onSelected("")
                        expanded = false
                    }
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
