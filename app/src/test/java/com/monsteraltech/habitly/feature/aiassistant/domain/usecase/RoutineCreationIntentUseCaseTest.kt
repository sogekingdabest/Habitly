package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCreationIntentUseCaseTest {

    private val useCase = RoutineCreationIntentUseCase()

    // ---------- Debe activar la extracción (crear rutinas) ----------

    @Test
    fun `propon rutinas is a creation intent`() {
        assertTrue(useCase("Propón rutinas útiles para mantener la casa ordenada"))
    }

    @Test
    fun `plan de limpieza is a creation intent`() {
        assertTrue(useCase("Proponme un plan de limpieza semanal para casa"))
    }

    @Test
    fun `creame rutinas ignoring accents is a creation intent`() {
        assertTrue(useCase("Créame unas rutinas de limpieza para la cocina"))
    }

    @Test
    fun `proponme habitos is a creation intent`() {
        assertTrue(useCase("proponme habitos para dormir mejor"))
    }

    @Test
    fun `anade una rutina is a creation intent`() {
        assertTrue(useCase("Añade una rutina de gimnasio los lunes"))
    }

    @Test
    fun `hazme una rutina is a creation intent`() {
        assertTrue(useCase("Hazme una rutina para mantener la cocina limpia"))
    }

    @Test
    fun `ponme una rutina is a creation intent`() {
        assertTrue(useCase("Ponme una rutina de estiramientos por la mañana"))
    }

    @Test
    fun `ponerme una rutina nueva is a creation intent`() {
        assertTrue(useCase("¿Puedes ponerme una rutina nueva?"))
    }

    @Test
    fun `agrega un habito is a creation intent`() {
        assertTrue(useCase("Agrega un hábito de leer antes de dormir"))
    }

    @Test
    fun `apuntame una rutina is a creation intent`() {
        assertTrue(useCase("Apúntame una rutina para regar las plantas"))
    }

    // ---------- NO debe activar la extracción (consulta / otro tema) ----------

    @Test
    fun `asking about existing routines is not a creation intent`() {
        assertFalse(useCase("¿Qué rutinas tengo hoy?"))
    }

    @Test
    fun `organize my day is not a creation intent`() {
        // Habla de las rutinas que YA tiene: no debe ofrecer crear nuevas.
        assertFalse(useCase("¿Cómo me organizo hoy con las rutinas que tengo pendientes? Dame un orden"))
    }

    @Test
    fun `cleaning tips is not a creation intent`() {
        assertFalse(useCase("Dame trucos de limpieza que ahorren tiempo en el día a día"))
    }

    @Test
    fun `a recipe request is not a creation intent`() {
        assertFalse(useCase("Dame ideas de cenas rápidas para esta noche"))
    }

    @Test
    fun `blank message is not a creation intent`() {
        assertFalse(useCase("   "))
    }

    @Test
    fun `word containing pon inside is not a creation intent`() {
        // "respondes" contiene "pon" pero no como inicio de palabra.
        assertFalse(useCase("¿Me respondes con las rutinas que tengo hoy?"))
    }

    @Test
    fun `me pongo con las rutinas is not a creation intent`() {
        // "pongo" es ponerse a hacerlas, no crearlas.
        assertFalse(useCase("Hoy me pongo con las rutinas pendientes"))
    }

    @Test
    fun `ideas creativas is not a creation intent`() {
        // "creativas" empieza por "crea" pero no es el verbo crear.
        assertFalse(useCase("Dame ideas creativas para mis rutinas de siempre"))
    }

    // ---------- Confirmación de seguimiento ("sí, créalas") ----------

    @Test
    fun `si crealas is a follow-up confirmation`() {
        assertTrue(useCase.isFollowUpConfirmation("Sí, créalas"))
    }

    @Test
    fun `bare affirmative is a follow-up confirmation`() {
        assertTrue(useCase.isFollowUpConfirmation("vale"))
    }

    @Test
    fun `ponlas is a follow-up confirmation`() {
        assertTrue(useCase.isFollowUpConfirmation("ponlas"))
    }

    @Test
    fun `a refusal is not a follow-up confirmation`() {
        assertFalse(useCase.isFollowUpConfirmation("no, mejor no"))
    }

    @Test
    fun `a long message is not a follow-up confirmation`() {
        assertFalse(
            useCase.isFollowUpConfirmation(
                "Pues mira, ahora que lo pienso preferiría hablar de otra cosa completamente distinta"
            )
        )
    }

    // ---------- Detección de propuestas del asistente ----------

    @Test
    fun `assistant proposal looks like a routine proposal`() {
        assertTrue(
            useCase.looksLikeRoutineProposal(
                "Te propongo estas rutinas para mantener la casa: fregar los lunes y barrer a diario."
            )
        )
    }

    @Test
    fun `listing existing routines is not a proposal`() {
        assertFalse(
            useCase.looksLikeRoutineProposal(
                "Hoy tienes estas rutinas pendientes: ir al gimnasio y fregar la cocina."
            )
        )
    }

    @Test
    fun `a shopping reply mentioning rutina in another sentence is not a proposal`() {
        // Regresión: una lista de la compra con "te recomiendo…" al principio y "rutina" de
        // pasada en otra frase ofrecía el chip de crear rutinas en plena charla de compra.
        assertFalse(
            useCase.looksLikeRoutineProposal(
                "Te recomiendo comprar pollo, arroz y verduras para la semana. " +
                    "Con esto cubres las cenas. Así llevarás mejor tu rutina."
            )
        )
    }

    @Test
    fun `marker next to the noun in the same sentence is a proposal`() {
        assertTrue(
            useCase.looksLikeRoutineProposal(
                "¿Quieres que te sugiera hábitos nuevos para la mañana?"
            )
        )
    }
}
