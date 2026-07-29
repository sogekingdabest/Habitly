package com.monsteraltech.habitly.feature.shopping.domain.util

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem

/**
 * Works out how the pantry ends up once new products go in.
 *
 * A pure function so it is testable and so both places that write to the pantry can share it: the
 * shopping archive, which does it inside its own atomic batch, and the pantry itself.
 */
object PantryMerge {

    /**
     * Returns the documents that need writing: [incoming] products added onto whatever [existing]
     * already held. Only what changes comes back.
     *
     * Quantities only add up when the unit matches. Mixing "2 kg" with "3 unidad" means nothing, so
     * in that case the existing entry is kept and only its date is refreshed. Converting between
     * units would be over-engineering for what it buys.
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
            // What this same batch has already accumulated counts the same as what was in the pantry.
            val current = result[id] ?: byId[id]

            result[id] = when {
                current == null -> item.copy(
                    id = id,
                    quantity = item.quantity.coerceAtLeast(1),
                    updatedAt = now
                )

                current.unit == item.unit -> current.copy(
                    quantity = (current.quantity + item.quantity).coerceIn(1, MAX_QUANTITY),
                    // A product can arrive from the AI with no category: do not wipe the one it had.
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

    /** Turns shopping-list products into pantry entries. */
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
