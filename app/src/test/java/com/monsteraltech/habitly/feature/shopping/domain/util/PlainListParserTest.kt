package com.monsteraltech.habitly.feature.shopping.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainListParserTest {

    // ---------- Dictado por voz ----------

    @Test
    fun `dictar tres productos devuelve tres`() {
        val result = PlainListParser.fromSpeech("leche, huevos y pan")

        assertEquals(3, result.size)
        assertEquals(listOf("Leche", "Huevos", "Pan"), result.map { it.name })
    }

    @Test
    fun `dos litros de leche sale con cantidad y unidad`() {
        val result = PlainListParser.fromSpeech("dos litros de leche")

        assertEquals(1, result.size)
        assertEquals("Leche", result[0].name)
        assertEquals(2, result[0].quantity)
        assertEquals("L", result[0].unit)
    }

    @Test
    fun `cantidad numerica pegada a la unidad`() {
        val result = PlainListParser.fromSpeech("500g de harina")

        assertEquals(1, result.size)
        assertEquals("Harina", result[0].name)
        assertEquals(500, result[0].quantity)
        assertEquals("g", result[0].unit)
    }

    @Test
    fun `numero sin unidad es cantidad y el resto nombre`() {
        val result = PlainListParser.fromSpeech("3 huevos")

        assertEquals(1, result.size)
        assertEquals("Huevos", result[0].name)
        assertEquals(3, result[0].quantity)
        assertEquals("unidad", result[0].unit)
    }

    @Test
    fun `una unidad inventada se queda en el nombre`() {
        val result = PlainListParser.fromSpeech("dos botellas de agua")

        assertEquals(1, result.size)
        assertEquals("Botellas de agua", result[0].name)
        assertEquals(2, result[0].quantity)
        assertEquals("unidad", result[0].unit)
    }

    @Test
    fun `la conjuncion no parte palabras que la contienen`() {
        val result = PlainListParser.fromSpeech("yogur, mayonesa")

        assertEquals(listOf("Yogur", "Mayonesa"), result.map { it.name })
    }

    @Test
    fun `dictado sin nada utilizable devuelve vacio`() {
        assertTrue(PlainListParser.fromSpeech("   ").isEmpty())
        assertTrue(PlainListParser.fromSpeech("...").isEmpty())
    }

    // ---------- Texto compartido, línea a línea ----------

    @Test
    fun `lista con vinetas y cantidades`() {
        val text = """
            Ingredientes:
            - 200 g de harina
            * 2 huevos
            • 1 L de leche
            1. Sal
        """.trimIndent()

        val result = PlainListParser.fromLines(text)

        assertEquals(4, result.size)
        assertEquals("Harina", result[0].name)
        assertEquals(200, result[0].quantity)
        assertEquals("g", result[0].unit)
        assertEquals("Huevos", result[1].name)
        assertEquals(2, result[1].quantity)
        assertEquals("Leche", result[2].name)
        assertEquals("L", result[2].unit)
        assertEquals("Sal", result[3].name)
    }

    @Test
    fun `los encabezados los enlaces y la prosa no son productos`() {
        val text = """
            Lista de la compra:
            https://ejemplo.com/receta
            Precalienta el horno a 180 grados y mezcla la harina con el azúcar hasta que quede una masa fina.
            Pan
        """.trimIndent()

        val result = PlainListParser.fromLines(text)

        assertEquals(1, result.size)
        assertEquals("Pan", result[0].name)
    }

    @Test
    fun `una lista en una sola linea se parte por comas`() {
        val result = PlainListParser.fromLines("leche, huevos, pan")

        assertEquals(3, result.size)
    }

    @Test
    fun `la y no parte las lineas de un texto largo`() {
        val result = PlainListParser.fromLines("Harina y azúcar")

        assertEquals(1, result.size)
        assertEquals("Harina y azúcar", result[0].name)
    }

    @Test
    fun `cantidad al final del nombre`() {
        val result = PlainListParser.fromLines("Leche x2")

        assertEquals(1, result.size)
        assertEquals("Leche", result[0].name)
        assertEquals(2, result[0].quantity)
    }

    @Test
    fun `los repetidos se deduplican sin distinguir tildes ni mayusculas`() {
        val result = PlainListParser.fromLines("Plátano\nplatano\nPLÁTANO")

        assertEquals(1, result.size)
    }

    @Test
    fun `se respeta el tope de productos`() {
        val text = (1..60).joinToString("\n") { "Producto $it" }

        assertEquals(PlainListParser.MAX_ITEMS, PlainListParser.fromLines(text).size)
    }

    @Test
    fun `casillas de una lista de tareas`() {
        val result = PlainListParser.fromLines("[ ] Sacar la basura\n[x] Fregar la cocina")

        assertEquals(listOf("Sacar la basura", "Fregar la cocina"), result.map { it.name })
    }
}
