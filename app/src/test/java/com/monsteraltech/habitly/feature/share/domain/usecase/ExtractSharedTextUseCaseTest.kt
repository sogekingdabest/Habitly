package com.monsteraltech.habitly.feature.share.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiRoutinesUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.ParseAiShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.presentation.FakeAiAssistantRepository
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineFrequency
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExtractSharedTextUseCaseTest {

    private val repository = FakeAiAssistantRepository()
    private val useCase = ExtractSharedTextUseCase(
        repository = repository,
        parseAiShoppingListUseCase = ParseAiShoppingListUseCase(),
        parseAiRoutinesUseCase = ParseAiRoutinesUseCase()
    )

    @Before
    fun setUp() {
        repository.reset()
    }

    @Test
    fun `una receta solo gasta la extraccion de la compra`() = runTest {
        repository.shoppingResult =
            """{"shopping_list":[{"name":"Harina","quantity":200,"unit":"g"}]}"""

        val result = useCase.withAi("Receta: 200 g de harina y 3 huevos")

        assertEquals(1, repository.extractShoppingCallCount)
        assertEquals(0, repository.extractRoutinesCallCount)
        assertEquals(1, result.products.size)
        assertEquals("Harina", result.products[0].name)
        assertTrue(result.usedAi)
    }

    @Test
    fun `un reparto de tareas solo gasta la extraccion de rutinas`() = runTest {
        repository.routinesResult =
            """{"routines":[{"title":"Sacar la basura","frequency":"diaria"}]}"""

        val result = useCase.withAi("Tareas del piso: sacar la basura y poner la lavadora")

        assertEquals(0, repository.extractShoppingCallCount)
        assertEquals(1, repository.extractRoutinesCallCount)
        assertEquals(1, result.routines.size)
        assertEquals("Sacar la basura", result.routines[0].title)
    }

    @Test
    fun `el texto llega al modelo delimitado como datos`() = runTest {
        useCase.withAi("Leche\nHuevos")

        val source = repository.lastShoppingSource
        assertTrue(source!!.contains("TEXTO RECIBIDO (INICIO)"))
        assertTrue(source.contains("TEXTO RECIBIDO (FIN)"))
        assertTrue(source.contains("DATOS"))
        assertTrue(source.contains("Leche"))
    }

    @Test
    fun `si el modelo no saca nada se cae al lector de texto plano`() = runTest {
        repository.shoppingResult = """{"shopping_list":[]}"""

        val result = useCase.withAi("2 kg de tomates\n1 L de leche")

        assertEquals(2, result.products.size)
        assertEquals("Tomates", result.products[0].name)
        assertEquals(2, result.products[0].quantity)
        assertEquals("kg", result.products[0].unit)
        // Lo propuesto ya no viene de la IA: la pantalla lo dice y ofrece reintentar.
        assertFalse(result.usedAi)
    }

    @Test
    fun `sin modelo el texto se lee por lineas`() {
        val result = useCase.withoutAi("- 2 kg de tomates\n- Leche")

        assertEquals(2, result.products.size)
        assertTrue(result.routines.isEmpty())
        assertFalse(result.usedAi)
    }

    @Test
    fun `sin modelo un texto de tareas se propone como rutinas diarias`() {
        val result = useCase.withoutAi("Tareas: fregar la cocina\nsacar la basura")

        assertTrue(result.products.isEmpty())
        assertEquals(2, result.routines.size)
        assertEquals(RoutineFrequency.DAILY, result.routines[0].frequency)
    }

    @Test
    fun `un texto sin nada utilizable no propone nada`() = runTest {
        val result = useCase.withAi("   ")

        assertTrue(result.isEmpty)
        assertEquals(0, repository.extractShoppingCallCount)
    }
}
