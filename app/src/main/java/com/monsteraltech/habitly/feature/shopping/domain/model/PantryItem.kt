package com.monsteraltech.habitly.feature.shopping.domain.model

/**
 * Un producto que hay en casa.
 *
 * La despensa es deliberadamente simple: solo responde "¿esto lo tengo?". Sin caducidades
 * ni escaneos, que es donde se atascan las apps que lo intentan. Su valor está en cruzarla
 * con la lista de la compra y con el asistente ("¿qué ceno con lo que tengo?").
 *
 * El [id] es el nombre normalizado
 * (ver [com.monsteraltech.habitly.feature.shopping.domain.util.ProductNameNormalizer]),
 * así que no puede haber dos entradas del mismo producto.
 */
data class PantryItem(
    var id: String = "",
    var name: String = "",
    var quantity: Int = 1,
    var unit: String = "unidad",
    var category: String = "",
    var updatedAt: Long = System.currentTimeMillis()
)
