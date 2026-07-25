package com.monsteraltech.habitly.feature.shopping.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.monsteraltech.habitly.R

/** Unidad por defecto de un producto; con ella y cantidad 1 no hace falta enseñar nada. */
const val DEFAULT_UNIT = "unidad"

/**
 * "2 kg", "3 unidades", "1 paquete" — bien formados en español y en inglés.
 *
 * Antes se concatenaba a mano añadiendo una "s" cuando la cantidad pasaba de uno, lo que
 * producía "2 kgs" y "3 unidads". Las unidades se guardan con su nombre en español (es lo
 * que hay en Firestore), así que aquí se traducen a un `<plurals>` por unidad; una unidad
 * inventada por el usuario cae en el formato genérico.
 */
@Composable
fun quantityWithUnit(quantity: Int, unit: String): String {
    val plural = PLURALS_BY_UNIT[unit.trim().lowercase()]
        ?: return stringResource(R.string.shopping_quantity_generic, quantity, unit)
    return pluralStringResource(plural, quantity, quantity)
}

/**
 * Cantidad y unidad de un producto, o nada cuando es "1 unidad": repetir lo que ya se da por
 * supuesto solo añade ruido a la fila.
 */
@Composable
fun ItemQuantityLabel(quantity: Int, unit: String, modifier: Modifier = Modifier) {
    if (quantity <= 1 && unit == DEFAULT_UNIT) return

    Text(
        text = quantityWithUnit(quantity, unit),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier
    )
}

private val PLURALS_BY_UNIT = mapOf(
    "unidad" to R.plurals.unit_unit,
    "kg" to R.plurals.unit_kg,
    "g" to R.plurals.unit_g,
    "l" to R.plurals.unit_liter,
    "ml" to R.plurals.unit_ml,
    "docena" to R.plurals.unit_dozen,
    "paquete" to R.plurals.unit_pack
)
