package com.monsteraltech.habitly.feature.shopping.domain.model

/**
 * A product that is at home.
 *
 * The pantry is deliberately simple: it only answers "do I have this?". No expiry dates, no
 * scanning — which is where the apps that attempt it get stuck. Its value is in crossing it with
 * the shopping list and the assistant ("what can I cook with what I have?").
 *
 * The [id] is the normalised name
 * (see [com.monsteraltech.habitly.feature.shopping.domain.util.ProductNameNormalizer]),
 * so there can never be two entries for the same product.
 */
data class PantryItem(
    var id: String = "",
    var name: String = "",
    var quantity: Int = 1,
    var unit: String = "unidad",
    var category: String = "",
    var updatedAt: Long = System.currentTimeMillis()
)
