package com.monsteraltech.habitly.feature.share.domain.util

import java.text.Normalizer

/** What to look for in a shared text: products, routines or both. */
enum class SharedTextKind {
    SHOPPING,
    ROUTINES,
    BOTH;

    val includesShopping: Boolean get() = this != ROUTINES
    val includesRoutines: Boolean get() = this != SHOPPING
}

/**
 * Classifies what a shared text is about **before** spending inferences on it.
 *
 * Each extraction is a full turn of the local model (tens of seconds the first time), so always
 * running both would double the wait for nothing: a recipe carries no routines and a chores list
 * carries no shopping.
 *
 * The heuristic is deliberately conservative and **the default case is shopping**: a bare list of
 * nouns ("leche, huevos, pan") is a shopping list. Routines are only looked for when household-chore
 * or routine/habit words appear.
 *
 * A pure function with no Android dependency, so it is testable under JUnit.
 */
object SharedTextClassifier {

    operator fun invoke(text: String): SharedTextKind {
        val normalized = text.normalizeForMatch()
        if (normalized.isBlank()) return SharedTextKind.SHOPPING

        val routineHits = ROUTINE_KEYWORDS.count { normalized.contains(it) }
        val shoppingHits = SHOPPING_KEYWORDS.count { normalized.contains(it) }

        return when {
            routineHits == 0 -> SharedTextKind.SHOPPING
            shoppingHits == 0 -> SharedTextKind.ROUTINES
            // A mixed text (a "week plan" with menu and chores) deserves both.
            else -> SharedTextKind.BOTH
        }
    }

    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Household chores and routine/habit vocabulary (unaccented), Spanish and English. */
    private val ROUTINE_KEYWORDS = listOf(
        "rutina", "habito", "tarea", "quehacer", "limpiar", "limpieza", "fregar", "barrer",
        "aspirar", "planchar", "lavadora", "colada", "basura", "reciclaje", "sabanas",
        "banos", "bano", "polvo", "riego", "regar", "ordenar", "recoger",
        "routine", "habit", "chore", "cleaning", "laundry", "dishes", "trash", "vacuum"
    )

    /** Food, ingredients and shopping (unaccented), Spanish and English. */
    private val SHOPPING_KEYWORDS = listOf(
        "receta", "ingrediente", "menu", "cena", "comida", "desayuno", "almuerzo", "merienda",
        "plato", "cocinar", "horno", "sarten", "compra", "supermercado", "gramos", "litros",
        "recipe", "ingredient", "dinner", "lunch", "breakfast", "grocer", "shopping"
    )
}
