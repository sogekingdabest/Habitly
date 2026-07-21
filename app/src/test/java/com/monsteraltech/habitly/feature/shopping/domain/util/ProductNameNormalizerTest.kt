package com.monsteraltech.habitly.feature.shopping.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductNameNormalizerTest {

    @Test
    fun `lowercases and trims`() {
        assertEquals("tomate", ProductNameNormalizer.normalize("  Tomate  "))
    }

    @Test
    fun `strips accents`() {
        assertEquals("platano", ProductNameNormalizer.normalize("Plátano"))
        assertEquals("jamon serrano", ProductNameNormalizer.normalize("Jamón Serrano"))
    }

    @Test
    fun `collapses inner whitespace`() {
        assertEquals("leche entera", ProductNameNormalizer.normalize("Leche   entera"))
    }

    @Test
    fun `same product is detected across spelling variants`() {
        assertTrue(ProductNameNormalizer.isSameProduct("Plátano", "  platano "))
        assertTrue(ProductNameNormalizer.isSameProduct("TOMATE", "tomate"))
        assertFalse(ProductNameNormalizer.isSameProduct("Tomate", "Tomates"))
    }

    // ---------- Ids de documento ----------

    @Test
    fun `document id is the normalized name`() {
        assertEquals("platano", ProductNameNormalizer.toDocumentId("Plátano"))
    }

    @Test
    fun `document id replaces characters firestore rejects`() {
        val id = ProductNameNormalizer.toDocumentId("Aceite/Oliva")

        assertEquals("aceite-oliva", id)
    }

    @Test
    fun `document id does not end up being dots`() {
        assertNull(ProductNameNormalizer.toDocumentId("."))
        assertNull(ProductNameNormalizer.toDocumentId(".."))
    }

    @Test
    fun `blank name has no document id`() {
        assertNull(ProductNameNormalizer.toDocumentId("   "))
    }

    @Test
    fun `very long names are truncated`() {
        val id = ProductNameNormalizer.toDocumentId("a".repeat(500))

        assertTrue((id?.length ?: 0) <= 120)
    }

    @Test
    fun `variants of the same product share the document id`() {
        assertEquals(
            ProductNameNormalizer.toDocumentId("Plátano"),
            ProductNameNormalizer.toDocumentId("  PLATANO ")
        )
    }
}
