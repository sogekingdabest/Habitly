package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingCreationIntentUseCaseTest {

    private val useCase = ShoppingCreationIntentUseCase()

    // ---------- Debe activar la extracción de la compra ----------

    @Test
    fun `asking for recipes is a shopping intent`() {
        assertTrue(useCase("¿Qué platos puedo preparar esta semana? Propón 3 recetas"))
    }

    @Test
    fun `weekly shopping list is a shopping intent`() {
        assertTrue(useCase("Hazme una lista de la compra semanal para casa"))
    }

    @Test
    fun `dinner ideas are a shopping intent`() {
        assertTrue(useCase("Dame ideas de cenas rápidas para esta noche"))
    }

    @Test
    fun `weekly menu ignoring accents is a shopping intent`() {
        assertTrue(useCase("Prepárame un menú semanal"))
    }

    @Test
    fun `adding items to the list is a shopping intent`() {
        assertTrue(useCase("Añade pan y huevos a la lista"))
    }

    // ---------- NO debe activar la extracción de la compra ----------

    @Test
    fun `cleaning plan is not a shopping intent`() {
        assertFalse(useCase("Proponme un plan de limpieza semanal para casa"))
    }

    @Test
    fun `routine ideas are not a shopping intent`() {
        assertFalse(useCase("Propón rutinas útiles para mantener la casa ordenada"))
    }

    @Test
    fun `cleaning the kitchen is not a shopping intent`() {
        assertFalse(useCase("¿Cómo limpio la cocina a fondo?"))
    }

    @Test
    fun `blank message is not a shopping intent`() {
        assertFalse(useCase("   "))
    }

    // ---------- Detección de propuestas del asistente ----------

    @Test
    fun `a shopping list reply looks like a shopping proposal`() {
        assertTrue(
            useCase.looksLikeShoppingProposal(
                "Aquí tienes la lista de la compra: pollo, arroz, tomates y aceite."
            )
        )
    }

    @Test
    fun `recommending ingredients looks like a shopping proposal`() {
        assertTrue(
            useCase.looksLikeShoppingProposal(
                "Te recomiendo estos ingredientes para el menú de la semana."
            )
        )
    }

    @Test
    fun `a routine proposal is not a shopping proposal`() {
        assertFalse(
            useCase.looksLikeShoppingProposal(
                "Te propongo estas rutinas para casa: fregar los lunes y barrer a diario."
            )
        )
    }

    @Test
    fun `mentioning food in another sentence is not a shopping proposal`() {
        assertFalse(
            useCase.looksLikeShoppingProposal(
                "Te propongo estas rutinas de orden. Después podrás preparar la cena con calma."
            )
        )
    }
}
