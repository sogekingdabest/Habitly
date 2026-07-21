package com.monsteraltech.habitly.feature.shopping.domain.util

import com.monsteraltech.habitly.feature.shopping.domain.model.PantryItem
import com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryMergeTest {

    private val now = 1_000_000L

    @Test
    fun `nothing to add returns nothing to write`() {
        val result = PantryMerge.merge(emptyList(), emptyList(), now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `new product enters the pantry`() {
        val incoming = listOf(PantryItem(name = "Tomate", quantity = 3, unit = "unidad"))

        val result = PantryMerge.merge(emptyList(), incoming, now)

        assertEquals(1, result.size)
        assertEquals("tomate", result[0].id)
        assertEquals(3, result[0].quantity)
        assertEquals(now, result[0].updatedAt)
    }

    @Test
    fun `same product with the same unit adds up`() {
        val existing = listOf(PantryItem(id = "tomate", name = "Tomate", quantity = 2, unit = "unidad"))
        val incoming = listOf(PantryItem(name = "tomate", quantity = 3, unit = "unidad"))

        val result = PantryMerge.merge(existing, incoming, now)

        assertEquals(1, result.size)
        assertEquals(5, result[0].quantity)
    }

    @Test
    fun `spelling variants land on the same entry`() {
        val existing = listOf(PantryItem(id = "platano", name = "Plátano", quantity = 2))
        val incoming = listOf(PantryItem(name = "  PLATANO ", quantity = 1))

        val result = PantryMerge.merge(existing, incoming, now)

        assertEquals(1, result.size)
        assertEquals("platano", result[0].id)
        assertEquals(3, result[0].quantity)
    }

    @Test
    fun `different units are not added up`() {
        val existing = listOf(PantryItem(id = "tomate", name = "Tomate", quantity = 2, unit = "kg"))
        val incoming = listOf(PantryItem(name = "Tomate", quantity = 3, unit = "unidad"))

        val result = PantryMerge.merge(existing, incoming, now)

        assertEquals(1, result.size)
        assertEquals("se conserva lo que ya había", 2, result[0].quantity)
        assertEquals("kg", result[0].unit)
        assertEquals("pero se refresca la fecha", now, result[0].updatedAt)
    }

    @Test
    fun `repeats inside the same batch also add up`() {
        val incoming = listOf(
            PantryItem(name = "Huevo", quantity = 6),
            PantryItem(name = "huevo", quantity = 6)
        )

        val result = PantryMerge.merge(emptyList(), incoming, now)

        assertEquals(1, result.size)
        assertEquals(12, result[0].quantity)
    }

    @Test
    fun `an incoming item without category does not wipe the existing one`() {
        val existing = listOf(
            PantryItem(id = "tomate", name = "Tomate", quantity = 1, category = "Frutas y Verduras")
        )
        val incoming = listOf(PantryItem(name = "Tomate", quantity = 1, category = ""))

        val result = PantryMerge.merge(existing, incoming, now)

        assertEquals("Frutas y Verduras", result[0].category)
    }

    @Test
    fun `products without a usable name are skipped`() {
        val incoming = listOf(PantryItem(name = "   ", quantity = 1))

        assertTrue(PantryMerge.merge(emptyList(), incoming, now).isEmpty())
    }

    @Test
    fun `untouched products are not rewritten`() {
        val existing = listOf(
            PantryItem(id = "tomate", name = "Tomate", quantity = 2),
            PantryItem(id = "arroz", name = "Arroz", quantity = 1)
        )
        val incoming = listOf(PantryItem(name = "Tomate", quantity = 1))

        val result = PantryMerge.merge(existing, incoming, now)

        assertEquals(1, result.size)
        assertEquals("tomate", result[0].id)
    }

    // ---------- Conversión desde la lista de la compra ----------

    @Test
    fun `shopping items become pantry items keeping quantity and unit`() {
        val bought = listOf(
            ShoppingItem(name = "Arroz", quantity = 2, unit = "kg", category = "Despensa y Conservas")
        )

        val result = PantryMerge.fromShoppingItems(bought)

        assertEquals(1, result.size)
        assertEquals("arroz", result[0].id)
        assertEquals("Arroz", result[0].name)
        assertEquals(2, result[0].quantity)
        assertEquals("kg", result[0].unit)
        assertEquals("Despensa y Conservas", result[0].category)
    }

    @Test
    fun `shopping items without a usable name are skipped`() {
        val result = PantryMerge.fromShoppingItems(listOf(ShoppingItem(name = "  ")))

        assertTrue(result.isEmpty())
    }
}
