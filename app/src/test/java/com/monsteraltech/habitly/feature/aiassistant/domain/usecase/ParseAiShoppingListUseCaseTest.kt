package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseAiShoppingListUseCaseTest {

    private val useCase = ParseAiShoppingListUseCase()

    @Test
    fun `parses marker followed by json object`() {
        val text = """
            Aquí tienes tu lista semanal:
            - Tomate
            - Leche
            @@LISTA@@ {"shopping_list":[{"name":"Tomate","quantity":6,"unit":"unidad","category":"Frutas y Verduras"},{"name":"Leche","quantity":2,"unit":"L","category":"Lacteos y Huevos"}]}
        """.trimIndent()

        val result = useCase(text)

        assertEquals(2, result.size)
        assertEquals("Tomate", result[0].name)
        assertEquals(6, result[0].quantity)
        assertEquals("L", result[1].unit)
        assertEquals("Lacteos y Huevos", result[1].category)
    }

    @Test
    fun `parses fenced json block without marker`() {
        val text = """
            Lista propuesta:
            ```json
            {"shopping_list":[{"name":"Pan"}]}
            ```
        """.trimIndent()

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Pan", result[0].name)
        assertEquals(1, result[0].quantity) // valor por defecto
        assertEquals("unidad", result[0].unit)
    }

    @Test
    fun `parses bare json array`() {
        val text = """[{"name":"Huevos","quantity":12,"unit":"unidad"}]"""

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Huevos", result[0].name)
        assertEquals(12, result[0].quantity)
    }

    @Test
    fun `parses spanish field names`() {
        val text = """@@LISTA@@ {"lista":[{"nombre":"Arroz","cantidad":1,"unidad":"kg","categoria":"Despensa y Conservas"}]}"""

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Arroz", result[0].name)
        assertEquals(1, result[0].quantity)
        assertEquals("kg", result[0].unit)
        assertEquals("Despensa y Conservas", result[0].category)
    }

    @Test
    fun `returns empty when there is no list`() {
        val text = "Para limpiar el baño, usa vinagre y bicarbonato. Frota bien y aclara."

        assertTrue(useCase(text).isEmpty())
    }

    @Test
    fun `regex fallback recovers items from malformed json`() {
        // JSON con coma final que puede romper un parser estricto.
        val text = """@@LISTA@@ {"shopping_list":[{"name":"Sal","quantity":1,},]}"""

        val result = useCase(text)

        assertEquals(1, result.size)
        assertEquals("Sal", result[0].name)
    }

    @Test
    fun `deduplicates items by name ignoring case`() {
        val text = """@@LISTA@@ {"shopping_list":[{"name":"Pan"},{"name":"pan"}]}"""

        val result = useCase(text)

        assertEquals(1, result.size)
    }

    @Test
    fun `ignores markdown links and other brackets`() {
        val text = "Puedes ver más recetas [aquí](https://example.com) cuando quieras."

        assertTrue(useCase(text).isEmpty())
    }
}
