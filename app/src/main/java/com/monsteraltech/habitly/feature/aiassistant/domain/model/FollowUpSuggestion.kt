package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * Qué extracción dispara el chip de seguimiento tras una propuesta sin tarjeta ("Sí, créalas",
 * "Sí, a la lista"). El destino se decide al detectar la propuesta (mirando QUÉ propone el
 * mensaje del asistente) y no se re-deriva del texto al pulsar el chip (eso ofrecía "crear
 * rutinas" tras una lista de la compra). La etiqueta, el prompt y el "voy" del chip los pone la
 * capa de presentación con `stringResource`, según este destino.
 */
enum class FollowUpTarget {
    ROUTINES, SHOPPING, BOTH;

    val includesRoutines: Boolean get() = this != SHOPPING
    val includesShopping: Boolean get() = this != ROUTINES
}
