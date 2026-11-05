package com.monsteraltech.habitly.feature.aiassistant.domain.util

import com.monsteraltech.habitly.feature.shopping.presentation.add.CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the vocabulary the assistant and the app share.
 *
 * The bug this closes was not a crash: the assistant wrote categories the picker does not offer
 * and nobody noticed, because an invented category looks exactly like a real one until you open
 * the pantry and find two headings for the same thing. So what matters here is that the two lists
 * cannot drift apart again without the build saying so.
 */
class AiShoppingCategoriesTest {

    @Test
    fun `the keys cover the app's categories, all of them and only them`() {
        assertEquals(CATEGORIES, AiShoppingCategories.BY_KEY.values.toList())
    }

    @Test
    fun `every key resolves to its category`() {
        for ((key, category) in AiShoppingCategories.BY_KEY) {
            assertEquals(category, AiShoppingCategories.resolve(key))
        }
    }

    @Test
    fun `a category written out by its own name also resolves`() {
        // Sin esquema —el fallback sin restringir y los bloques @@LISTA@@ de una respuesta normal—
        // el modelo escribe el nombre, no la clave, y rara vez con los acentos puestos.
        assertEquals("Lácteos", AiShoppingCategories.resolve("Lácteos"))
        assertEquals("Lácteos", AiShoppingCategories.resolve("lacteos"))
        assertEquals("Frutas y Verduras", AiShoppingCategories.resolve("FRUTAS Y VERDURAS"))
        assertEquals("Especias y Condimentos", AiShoppingCategories.resolve(" Especias y Condimentos "))
    }

    @Test
    fun `a category the app does not have becomes no category at all`() {
        // Las que el prompt se inventaba hasta ahora. En blanco la despensa las agrupa bajo
        // «Otros» y PantryMerge respeta la categoría que el producto ya tuviera; con el nombre
        // inventado, cada una abría su propio grupo.
        assertEquals("", AiShoppingCategories.resolve("Despensa y Conservas"))
        assertEquals("", AiShoppingCategories.resolve("Lacteos y Huevos"))
        assertEquals("", AiShoppingCategories.resolve("Panaderia y Cereales"))
        assertEquals("", AiShoppingCategories.resolve("Limpieza y Hogar"))
        assertEquals("", AiShoppingCategories.resolve(""))
    }
}
