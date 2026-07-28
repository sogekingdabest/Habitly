package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import java.text.Normalizer
import javax.inject.Inject

/**
 * Decides whether the user's message opens the door to proposing shopping-list items — recipes,
 * menus, dinners or the shopping itself. This is the shopping second turn's intent gate: on `false`
 * no extraction runs and no shopping card appears.
 *
 * Unlike the routine gate, detecting a food or shopping context is enough here: the extractor itself
 * returns an empty list when the text recommends buying nothing, so the gate only has to keep the
 * extraction from running on clearly unrelated messages — cleaning, routines, general questions.
 */
class ShoppingCreationIntentUseCase @Inject constructor() {

    operator fun invoke(userMessage: String): Boolean {
        val text = userMessage.normalizeForMatch()
        if (text.isBlank()) return false
        return KEYWORDS.any { text.contains(it) }
    }

    /**
     * Heuristic for "the assistant just proposed items or a list": either it says "lista de la
     * compra" verbatim, or it uses proposal language near a food/shopping term in the same sentence.
     * Proximity follows the same rule as the routine proposal: a stray word in another sentence does
     * not raise the follow-up chip.
     */
    fun looksLikeShoppingProposal(assistantText: String): Boolean {
        val text = assistantText.normalizeForMatch()
        if (text.isBlank()) return false
        if (text.contains(SHOPPING_LIST_PHRASE)) return true
        return PROPOSAL_NEAR_KEYWORD_REGEX.containsMatchIn(text)
    }

    /** Lowercased and unaccented, so matching depends on neither accents nor case. */
    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private companion object {
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")

        /** Unaccented food/cooking/shopping terms where proposing items makes sense. "lista" covers
         *  "añade eso a la lista"; the extractor filters out messages with nothing to buy. */
        val KEYWORDS = listOf(
            "receta", "menu", "cena", "comida", "desayuno", "almuerzo", "merienda",
            "plato", "ingrediente", "cocinar", "cocino", "guiso", "compra", "lista"
        )

        /** Unambiguous phrase for a shopping list, unaccented. */
        const val SHOPPING_LIST_PHRASE = "lista de la compra"

        /** The assistant's proposal language, unaccented (conjugation prefixes). */
        val PROPOSAL_MARKERS = listOf(
            "propong", "sugier", "recomiend", "recomendar", "aqui tienes", "necesitas comprar"
        )

        /** Marker-to-term window within the same sentence, in characters. */
        const val PROPOSAL_WINDOW_CHARS = 80

        /** Proposal marker and food/shopping term close together, without crossing `.!?` or a newline. */
        val PROPOSAL_NEAR_KEYWORD_REGEX = run {
            val marker = "(?:${PROPOSAL_MARKERS.joinToString("|")})"
            val keyword = "(?:${KEYWORDS.joinToString("|")})"
            val gap = "[^.!?\\n]{0,$PROPOSAL_WINDOW_CHARS}"
            Regex("$marker$gap$keyword|$keyword$gap$marker")
        }
    }
}
