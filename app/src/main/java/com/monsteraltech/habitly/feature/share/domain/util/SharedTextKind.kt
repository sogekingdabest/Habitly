package com.monsteraltech.habitly.feature.share.domain.util

import java.text.Normalizer

/** Qué se busca en un texto compartido: productos, rutinas o las dos cosas. */
enum class SharedTextKind {
    SHOPPING,
    ROUTINES,
    BOTH;

    val includesShopping: Boolean get() = this != ROUTINES
    val includesRoutines: Boolean get() = this != SHOPPING
}

/**
 * Clasifica de qué va un texto compartido **antes** de gastar inferencias en él.
 *
 * Cada extracción es un turno completo del modelo local (decenas de segundos la primera vez), así
 * que lanzar las dos siempre duplicaría la espera para nada: una receta no trae rutinas y una
 * lista de tareas de casa no trae la compra.
 *
 * La heurística es a propósito conservadora y **el caso por defecto es la compra**: una lista
 * suelta de sustantivos ("leche, huevos, pan") es una lista de la compra. Solo se buscan rutinas
 * si aparecen palabras de tarea doméstica o de rutina/hábito.
 *
 * Función pura (sin Android) para poder testearla con JUnit.
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
            else -> SharedTextKind.BOTH
        }
    }

    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Tareas de casa y vocabulario de rutina/hábito (sin tildes), español e inglés. */
    private val ROUTINE_KEYWORDS = listOf(
        "rutina", "habito", "tarea", "quehacer", "limpiar", "limpieza", "fregar", "barrer",
        "aspirar", "planchar", "lavadora", "colada", "basura", "reciclaje", "sabanas",
        "banos", "bano", "polvo", "riego", "regar", "ordenar", "recoger",
        "routine", "habit", "chore", "cleaning", "laundry", "dishes", "trash", "vacuum"
    )

    /** Comida, ingredientes y compra (sin tildes), español e inglés. */
    private val SHOPPING_KEYWORDS = listOf(
        "receta", "ingrediente", "menu", "cena", "comida", "desayuno", "almuerzo", "merienda",
        "plato", "cocinar", "horno", "sarten", "compra", "supermercado", "gramos", "litros",
        "recipe", "ingredient", "dinner", "lunch", "breakfast", "grocer", "shopping"
    )
}
