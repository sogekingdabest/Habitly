package com.monsteraltech.habitly.feature.shopping.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.monsteraltech.habitly.R

/** A product's default unit; with it and a quantity of 1 there is nothing worth showing. */
const val DEFAULT_UNIT = "unidad"

/**
 * "2 kg", "3 unidades", "1 paquete" — correctly formed in both Spanish and English.
 *
 * This used to be hand-concatenated with an "s" appended past a quantity of one, which produced
 * "2 kgs" and "3 unidads". Units are stored under their Spanish names, which is what Firestore
 * holds, so here each maps to its own `<plurals>`; a unit the user invented falls through to the
 * generic format.
 */
@Composable
fun quantityWithUnit(quantity: Int, unit: String): String {
    val plural = PLURALS_BY_UNIT[unit.trim().lowercase()]
        ?: return stringResource(R.string.shopping_quantity_generic, quantity, unit)
    return pluralStringResource(plural, quantity, quantity)
}

/**
 * A product's quantity and unit, or nothing at all when it is "1 unidad": restating what is already
 * assumed only adds noise to the row.
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
