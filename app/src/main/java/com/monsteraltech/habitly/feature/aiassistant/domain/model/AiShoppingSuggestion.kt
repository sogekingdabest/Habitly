package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * Un producto propuesto por el asistente de IA para añadir a la lista de la compra.
 * Se extrae del bloque estructurado que el modelo añade al final de sus respuestas.
 */
data class AiShoppingSuggestion(
    val name: String,
    val quantity: Int = 1,
    val unit: String = "unidad",
    val category: String = ""
)
