package com.monsteraltech.habitly.feature.share.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextClassifierTest {

    @Test
    fun `una receta va a la lista de la compra`() {
        val text = "Receta de bizcocho. Ingredientes: 200 g de harina, 3 huevos, 1 L de leche."

        assertEquals(SharedTextKind.SHOPPING, SharedTextClassifier(text))
    }

    @Test
    fun `una lista suelta de productos va a la compra por defecto`() {
        assertEquals(SharedTextKind.SHOPPING, SharedTextClassifier("leche\nhuevos\npan"))
    }

    @Test
    fun `un reparto de tareas va a rutinas`() {
        val text = "Tareas del piso: sacar la basura, poner la lavadora, limpiar el baño"

        assertEquals(SharedTextKind.ROUTINES, SharedTextClassifier(text))
    }

    @Test
    fun `un texto mixto busca las dos cosas`() {
        val text = "Plan de la semana: menú de cenas y las tareas de limpieza de cada día"

        assertEquals(SharedTextKind.BOTH, SharedTextClassifier(text))
    }

    @Test
    fun `un texto vacio no rompe nada`() {
        assertEquals(SharedTextKind.SHOPPING, SharedTextClassifier("   "))
    }

    @Test
    fun `las banderas de cada tipo son coherentes`() {
        assertEquals(true, SharedTextKind.SHOPPING.includesShopping)
        assertEquals(false, SharedTextKind.SHOPPING.includesRoutines)
        assertEquals(false, SharedTextKind.ROUTINES.includesShopping)
        assertEquals(true, SharedTextKind.ROUTINES.includesRoutines)
        assertEquals(true, SharedTextKind.BOTH.includesShopping)
        assertEquals(true, SharedTextKind.BOTH.includesRoutines)
    }
}
