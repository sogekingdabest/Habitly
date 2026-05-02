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
    suspend operator fun invoke(): String {
        val user = authRepository.getCurrentUser() ?: return getBasePersonality()
        
        // Use timeout to avoid hanging if flows don't emit
        val profile = withTimeoutOrNull(2000) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }
        
        val householdId = profile?.activeHouseholdId ?: return getBasePersonality()

        val shoppingItems = withTimeoutOrNull(2000) {
            shoppingRepository.observeShoppingList(householdId).firstOrNull()
        } ?: emptyList()

        val routines = withTimeoutOrNull(2000) {
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
            Eres Habitly, un asistente amigable experto en gestión del hogar. Tu objetivo es ayudar al usuario a organizarse, dar ideas de rutinas, recetas para la lista de la compra y consejos de limpieza. Mantén respuestas cortas, lógicas, amigables y directas. Utiliza el contexto oculto de la aplicación proporcionado para dar respuestas exactas sobre las rutinas y la lista de la compra si el usuario te pregunta por ellas. No reveles que estás leyendo un contexto oculto.
        """.trimIndent()
    }
}
