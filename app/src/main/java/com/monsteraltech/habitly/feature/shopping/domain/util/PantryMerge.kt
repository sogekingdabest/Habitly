package com.monsteraltech.habitly.feature.shopping.domain.util

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem

/**
 * Decide cómo queda la despensa al meter productos nuevos.
 *
 * Función pura para poder testearla y para que la compartan los dos sitios que escriben
 * en la despensa: el archivado de la compra (que lo hace dentro de su mismo batch atómico)
 * y la despensa en sí.
 */
object PantryMerge {

    /**
     * Devuelve los documentos que hay que escribir: los productos de [incoming] sumados a
     * lo que ya hubiera en [existing]. Solo devuelve lo que cambia.
     *
     * Las cantidades solo se suman si la unidad coincide. Mezclar "2 kg" con "3 unidad" no
     * significa nada, así que en ese caso se conserva lo que ya había y solo se refresca la
     * fecha. Convertir unidades sería sobreingeniería para lo que aporta.
     */
    fun merge(
        existing: List<PantryItem>,
        incoming: List<PantryItem>,
        now: Long = System.currentTimeMillis()
    ): List<PantryItem> {
        if (incoming.isEmpty()) return emptyList()

        val byId = existing.associateBy { it.id }
        val result = LinkedHashMap<String, PantryItem>()

        for (item in incoming) {
            val id = ProductNameNormalizer.toDocumentId(item.name) ?: continue
            val current = result[id] ?: byId[id]

            result[id] = when {
                current == null -> item.copy(
                    id = id,
                    quantity = item.quantity.coerceAtLeast(1),
                    updatedAt = now
                )

                current.unit == item.unit -> current.copy(
                    quantity = (current.quantity + item.quantity).coerceIn(1, MAX_QUANTITY),
                    category = item.category.ifBlank { current.category },
                    updatedAt = now
                )

                else -> current.copy(
                    category = item.category.ifBlank { current.category },
                    updatedAt = now
                )
            }
        }

        return result.values.toList()
    }

    /** Convierte productos de la lista de la compra en entradas de despensa. */
    fun fromShoppingItems(items: List<ShoppingItem>): List<PantryItem> =
        items.mapNotNull { item ->
            val id = ProductNameNormalizer.toDocumentId(item.name) ?: return@mapNotNull null
            PantryItem(
                id = id,
                name = item.name.trim(),
                quantity = item.quantity.coerceAtLeast(1),
                unit = item.unit,
                category = item.category
            )
        }

    private const val MAX_QUANTITY = 9999
}
