package com.monsteraltech.habitly.feature.aiassistant.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStructuredBlocksTest {

    // ---------- stripFromDisplay ----------

    @Test
    fun `plain text is left untouched`() {
        val text = "Puedes cocinar una tortilla de patatas."

        assertEquals(text, AiStructuredBlocks.stripFromDisplay(text))
    }

    @Test
    fun `shopping marker and its json are hidden`() {
        val text = """
            Aquí tienes la receta.
            @@LISTA@@ {"shopping_list":[{"name":"Huevo","quantity":4}]}
        """.trimIndent()

        val result = AiStructuredBlocks.stripFromDisplay(text)

        assertEquals("Aquí tienes la receta.", result)
        assertFalse(result.contains("shopping_list"))
    }

    @Test
    fun `routines marker and its json are hidden`() {
        val text = """
            Te propongo estas rutinas.
            @@RUTINA@@ {"routines":[{"title":"Fregar"}]}
        """.trimIndent()

        val result = AiStructuredBlocks.stripFromDisplay(text)

        assertEquals("Te propongo estas rutinas.", result)
        assertFalse(result.contains("routines"))
    }

    @Test
    fun `with both blocks everything from the first marker is hidden`() {
        val text = """
            Aquí tienes todo.
            @@RUTINA@@ {"routines":[{"title":"Fregar"}]}
            @@LISTA@@ {"shopping_list":[{"name":"Lejía"}]}
        """.trimIndent()

        val result = AiStructuredBlocks.stripFromDisplay(text)

        assertEquals("Aquí tienes todo.", result)
        assertFalse(result.contains("@@"))
    }

    @Test
    fun `fenced block without marker is hidden too`() {
        val text = """
            Aquí tienes la lista.
            ```json
            {"shopping_list":[{"name":"Huevo"}]}
            ```
        """.trimIndent()

        val result = AiStructuredBlocks.stripFromDisplay(text)

        assertFalse(result.contains("shopping_list"))
        assertTrue(result.contains("Aquí tienes la lista."))
    }

    @Test
    fun `unfinished fence during streaming is hidden`() {
        val text = """
            Aquí tienes la lista.
            ```json
            {"routines":[{"title":"Fre
        """.trimIndent()

        val result = AiStructuredBlocks.stripFromDisplay(text)

        assertEquals("Aquí tienes la lista.", result)
    }

    @Test
    fun `half written marker during streaming hides the rest`() {
        val text = "Aquí tienes la receta.\n@@LISTA@@ {\"shopping_l"

        assertEquals("Aquí tienes la receta.", AiStructuredBlocks.stripFromDisplay(text))
    }

    // ---------- hasPendingStructuredBlock ----------

    @Test
    fun `plain conversational text has no pending block`() {
        assertFalse(AiStructuredBlocks.hasPendingStructuredBlock("Aquí tienes la receta."))
        assertFalse(AiStructuredBlocks.hasPendingStructuredBlock(""))
    }

    @Test
    fun `full marker means a pending block`() {
        val streaming = "Aquí tienes la receta.\n@@LISTA@@ {\"shopping_l"

        assertTrue(AiStructuredBlocks.hasPendingStructuredBlock(streaming))
    }

    @Test
    fun `half written marker already counts as pending block`() {
        assertTrue(AiStructuredBlocks.hasPendingStructuredBlock("Aquí tienes la receta.\n@@LIS"))
    }

    // ---------- extractJsonRegion ----------

    @Test
    fun `extracts the region after its own marker`() {
        val text = """@@LISTA@@ {"shopping_list":[{"name":"Huevo"}]}"""

        val region = AiStructuredBlocks.extractJsonRegion(text, AiStructuredBlocks.SHOPPING_MARKER)

        // Devuelve el array interior; los parsers aceptan tanto el array suelto
        // como el objeto envoltorio, así que lo que importa es que traiga los datos.
        assertTrue(region!!.contains("Huevo"))
    }

    @Test
    fun `each marker extracts only its own block`() {
        val text = """
            @@RUTINA@@ {"routines":[{"title":"Fregar"}]}
            @@LISTA@@ {"shopping_list":[{"name":"Lejía"}]}
        """.trimIndent()

        val routines = AiStructuredBlocks.extractJsonRegion(text, AiStructuredBlocks.ROUTINES_MARKER)
        val shopping = AiStructuredBlocks.extractJsonRegion(text, AiStructuredBlocks.SHOPPING_MARKER)

        assertTrue(routines!!.contains("Fregar"))
        assertFalse("no debe invadir el bloque de la compra", routines.contains("Lejía"))
        assertTrue(shopping!!.contains("Lejía"))
        assertFalse("no debe invadir el bloque de rutinas", shopping.contains("Fregar"))
    }

    @Test
    fun `text without json returns null`() {
        assertEquals(null, AiStructuredBlocks.extractJsonRegion("Hola qué tal", AiStructuredBlocks.SHOPPING_MARKER))
    }
}
