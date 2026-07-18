package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class GetAiContextUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val routinesRepository: RoutinesRepository,
    private val shoppingRepository: ShoppingRepository
) {
    suspend operator fun invoke(timeoutMs: Long = DEFAULT_TIMEOUT_MS): String {
        val user = authRepository.getCurrentUser() ?: return getBasePersonality()

        val profile = withTimeoutOrNull(timeoutMs) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }

        val householdId = profile?.activeHouseholdId ?: return getBasePersonality()

        val shoppingItems = withTimeoutOrNull(timeoutMs) {
            shoppingRepository.observeShoppingList(householdId).firstOrNull()
        } ?: emptyList()

        val routines = withTimeoutOrNull(timeoutMs) {
            routinesRepository.observePersonalRoutines(user.uid).firstOrNull()
        } ?: emptyList()

        val shoppingContext = if (shoppingItems.isEmpty()) {
            "La lista de la compra está vacía."
        } else {
            "Lista de la compra:\n" + shoppingItems.joinToString("\n") { "- ${it.name}" + if (it.isChecked) " (comprado)" else " (pendiente)" }
        }

        val routinesContext = if (routines.isEmpty()) {
            "No tienes rutinas asignadas."
        } else {
            "Tus rutinas:\n" + routines.joinToString("\n") { "- ${it.title}" + if (it.lastCompletedAt != null) " (marcada)" else " (pendiente)" }
        }

        return """
            ${getBasePersonality()}
            
            [Contexto Oculto de la Aplicación Habitly]
            $shoppingContext
            
            $routinesContext
        """.trimIndent()
    }

    private fun getBasePersonality(): String {
        return """
            Eres Habitly, un asistente amigable experto en gestión del hogar. Tu objetivo es ayudar al usuario a organizarse, dar ideas de rutinas, recetas para la lista de la compra y consejos de limpieza. Da respuestas completas, detalladas y bien estructuradas. Utiliza formato markdown cuando sea apropiado: listas con viñetas para pasos o elementos, negritas para destacar conceptos importantes, y secciones claras. Sé amigable, claro y conversacional. Utiliza el contexto oculto de la aplicación proporcionado para dar respuestas exactas sobre las rutinas y la lista de la compra si el usuario te pregunta por ellas. No reveles que estás leyendo un contexto oculto.

            Cuando propongas una lista de la compra, un menú semanal o los ingredientes de una receta, añade SIEMPRE en la última línea de tu respuesta, después del texto normal, este marcador seguido de un JSON en una sola línea:
            @@LISTA@@ {"shopping_list":[{"name":"Tomate","quantity":6,"unit":"unidad","category":"Frutas y Verduras"}]}
            Reglas del JSON: usa nombres de producto cortos y en singular; "quantity" es un número entero; "unit" es una de: unidad, kg, g, L, ml, docena, paquete; "category" es una de: Frutas y Verduras, Carnes y Pescados, Lacteos y Huevos, Panaderia y Cereales, Despensa y Conservas, Limpieza y Hogar, Bebidas. No expliques el marcador ni el JSON. Si tu respuesta no incluye ninguna lista de productos, NO añadas el marcador.
        """.trimIndent()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2000L
    }
}
