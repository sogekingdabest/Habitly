package com.monsteraltech.habitly.feature.aiassistant.domain.util

import java.text.Normalizer

/**
 * The vocabulary of shopping categories the model may use, and how it becomes the app's own.
 *
 * The assistant and the picker had never agreed: the extraction prompt dictated seven categories
 * of its own invention, the app offers ten, and only three names existed in both. Nothing checked
 * the value — it went straight into the product — so the assistant had been creating products with
 * a category the picker does not offer, which the pantry then shows as a heading of its own next
 * to the real one. The schema now closes the list ([AiExtractionSchemas.SHOPPING]) and this is
 * what the closed list means.
 *
 * The model works in **keys**, one ASCII word each: an enum of the names as the user reads them
 * breaks the engine's grammar on the first value containing a space — the reasoning is in
 * [AiExtractionSchemas]. The keys never leave this layer; [resolve] turns them into the name the
 * app shows, which is the only form that reaches a product.
 *
 * A key is narrower than the name it stands for — `frutas` for "Frutas y Verduras" — so the
 * extraction prompt glosses each one. That division is the point of having both: the grammar
 * settles what the model may write, and only the prompt can teach it what the words mean.
 *
 * [resolve] also takes the names themselves, accents or not, because the schema is not in force
 * everywhere: the fallback without constrained decoding and the `@@LISTA@@` block of an ordinary
 * answer both arrive unconstrained, and there the model writes whatever it likes. Anything it
 * cannot place becomes **no category at all** rather than a category the app does not have. Blank
 * is already a case everything downstream handles: the pantry groups it under "Otros" and
 * `PantryMerge` keeps whatever category the product already had.
 */
object AiShoppingCategories {

    // Declarado el primero porque BY_NAME ya lo usa al construirse, y un `object` se inicializa de
    // arriba abajo: más abajo, el mapa se queda a medias y la clase no llega a cargar.
    private val DIACRITICS = Regex("\\p{Mn}+")

    /**
     * Key the model writes -> category the app offers, in the order `CATEGORIES` lists them.
     *
     * Duplicated from `CATEGORIES` in `QuickAddSheet.kt` on purpose, the same way the schemas
     * duplicate `UNITS`: these are a contract with the model, and `AiShoppingCategoriesTest` fails
     * the build the day the two drift apart.
     */
    val BY_KEY: Map<String, String> = linkedMapOf(
        "frutas" to "Frutas y Verduras",
        "lacteos" to "Lácteos",
        "carnes" to "Carnes y Pescados",
        "panaderia" to "Panadería",
        "bebidas" to "Bebidas",
        "limpieza" to "Limpieza",
        "snacks" to "Snacks",
        "congelados" to "Congelados",
        "especias" to "Especias y Condimentos",
        "otros" to "Otros"
    )

    /** The same categories indexed by their own name, unaccented and lowercased. */
    private val BY_NAME: Map<String, String> = BY_KEY.values.associateBy { it.normalise() }

    /**
     * The app's category for whatever the model wrote — a key, or the name itself however it
     * spelled it — or `""` when it is neither.
     */
    fun resolve(raw: String): String {
        val value = raw.normalise()
        if (value.isEmpty()) return ""
        return BY_KEY[value] ?: BY_NAME[value] ?: ""
    }

    private fun String.normalise(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
}
