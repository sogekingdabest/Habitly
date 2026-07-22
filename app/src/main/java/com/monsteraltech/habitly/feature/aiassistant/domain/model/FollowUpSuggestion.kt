package com.monsteraltech.habitly.feature.aiassistant.domain.model

/** Qué extracción dispara el chip de seguimiento tras una propuesta sin tarjeta. */
enum class FollowUpTarget {
    ROUTINES, SHOPPING, BOTH;

    val includesRoutines: Boolean get() = this != SHOPPING
    val includesShopping: Boolean get() = this != ROUTINES
}

/**
 * Chip de seguimiento tras una propuesta del asistente que se quedó sin tarjeta ("Sí,
 * créalas", "Sí, a la lista"). A diferencia de [AiQuickPrompt], lleva el destino de la
 * extracción decidido en el momento de crearlo, mirando QUÉ propone el mensaje del
 * asistente: el texto visible es pura conversación y la intención no se re-deriva del
 * texto con regex al pulsarlo (eso ofrecía "crear rutinas" tras una lista de la compra).
 */
data class FollowUpSuggestion(
    val label: String,
    val prompt: String,
    val target: FollowUpTarget
)
