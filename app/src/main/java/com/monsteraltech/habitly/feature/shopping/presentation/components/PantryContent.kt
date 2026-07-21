package com.monsteraltech.habitly.feature.shopping.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem

/**
 * Vista de la despensa: lo que hay en casa, agrupado por categoría.
 *
 * Es deliberadamente simple (sin caducidades ni escaneos). Su valor está en que el asistente
 * la lee para responder "¿qué ceno con lo que tengo?" y para pedir solo lo que falta.
 */
@Composable
fun PantryContent(
    itemsByCategory: Map<String, List<PantryItem>>,
    onAdjustQuantity: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (itemsByCategory.isEmpty()) {
        PantryEmptyState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsByCategory.forEach { (category, items) ->
            item(key = "pantry-$category") {
                PantryCategoryCard(
                    category = category,
                    items = items,
                    onAdjustQuantity = onAdjustQuantity,
                    onDelete = onDelete
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PantryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Kitchen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.pantry_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.pantry_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PantryCategoryCard(
    category: String,
    items: List<PantryItem>,
    onAdjustQuantity: (String, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    pluralStringResource(R.plurals.shopping_products_count, items.size, items.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            items.forEach { item ->
                PantryItemRow(
                    item = item,
                    onAdjustQuantity = onAdjustQuantity,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun PantryItemRow(
    item: PantryItem,
    onAdjustQuantity: (String, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { onAdjustQuantity(item.id, -1) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.pantry_decrease, item.name),
                modifier = Modifier.size(18.dp)
            )
        }

        // La cantidad ya la anuncian los botones; leerla otra vez sería ruido.
        Text(
            text = "${item.quantity} ${item.unit}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .width(72.dp)
                .clearAndSetSemantics { }
        )

        IconButton(
            onClick = { onAdjustQuantity(item.id, 1) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.pantry_increase, item.name),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.pantry_remove, item.name),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Aviso de "esto ya lo tienes en casa" para el alta de productos. */
@Composable
fun PantryHint(quantity: Int, unit: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = stringResource(R.string.pantry_already_have, quantity, unit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}
