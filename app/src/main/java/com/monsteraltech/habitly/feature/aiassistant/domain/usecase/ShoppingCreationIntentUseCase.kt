package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import java.text.Normalizer
import javax.inject.Inject

/**
 * Decide si el mensaje del usuario abre la puerta a proponer productos para la lista de la compra
 * (recetas, menús, cenas o compra en sí). Es la "puerta de intención" del segundo turno de la
 * lista: si devuelve `false`, no se lanza la extracción y no aparece tarjeta de compra.
 *
 * A diferencia de la de rutinas, aquí basta con detectar contexto de comida/compra: el propio
 * extractor devuelve una lista vacía si el texto no recomienda comprar nada, así que la puerta solo
 * evita lanzar la extracción en mensajes claramente ajenos (limpieza, rutinas, dudas generales).
 */
class ShoppingCreationIntentUseCase @Inject constructor() {

    operator fun invoke(userMessage: String): Boolean {
        val text = userMessage.normalizeForMatch()
        if (text.isBlank()) return false
        return KEYWORDS.any { text.contains(it) }
    }

    /**
     * Heurística de "el asistente acaba de proponer productos o una lista": o dice "lista de
     * la compra" tal cual, o usa lenguaje de propuesta cerca (misma frase) de un término de
     * comida/compra. La cercanía sigue el mismo criterio que la propuesta de rutinas: una
     * palabra suelta en otra frase no dispara el chip de seguimiento.
     */
    fun looksLikeShoppingProposal(assistantText: String): Boolean {
        val text = assistantText.normalizeForMatch()
        if (text.isBlank()) return false
        if (text.contains(SHOPPING_LIST_PHRASE)) return true
        return PROPOSAL_NEAR_KEYWORD_REGEX.containsMatchIn(text)
    }

    /** Minúsculas y sin tildes, para comparar sin depender de acentos ni mayúsculas. */
    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private companion object {
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")

        /** Términos de comida/cocina/compra donde tiene sentido proponer productos (sin tildes).
         *  "lista" cubre "añade eso a la lista" (el extractor filtra si no hay nada que comprar). */
        val KEYWORDS = listOf(
            "receta", "menu", "cena", "comida", "desayuno", "almuerzo", "merienda",
            "plato", "ingrediente", "cocinar", "cocino", "guiso", "compra", "lista"
        )

        /** Frase que identifica una lista de la compra sin ambigüedad (sin tildes). */
        const val SHOPPING_LIST_PHRASE = "lista de la compra"

        /** Lenguaje de propuesta del asistente (sin tildes; prefijos de conjugación). */
        val PROPOSAL_MARKERS = listOf(
            "propong", "sugier", "recomiend", "recomendar", "aqui tienes", "necesitas comprar"
        )

        /** Ventana marcador↔término dentro de la misma frase (en caracteres). */
        const val PROPOSAL_WINDOW_CHARS = 80

        /** Marcador de propuesta y término de comida/compra cerca y sin cruzar `.!?` ni salto. */
        val PROPOSAL_NEAR_KEYWORD_REGEX = run {
            val marker = "(?:${PROPOSAL_MARKERS.joinToString("|")})"
            val keyword = "(?:${KEYWORDS.joinToString("|")})"
            val gap = "[^.!?\\n]{0,$PROPOSAL_WINDOW_CHARS}"
            Regex("$marker$gap$keyword|$keyword$gap$marker")
        }
    }
}
