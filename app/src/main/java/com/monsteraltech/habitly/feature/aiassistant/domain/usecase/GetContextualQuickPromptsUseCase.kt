package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiQuickPrompt
import com.monsteraltech.habitly.feature.aiassistant.domain.model.QuickPromptId
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.feature.shopping.domain.repository.PantryRepository
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Decide qué chips de sugerencia se muestran encima del input del chat según el momento
 * y el estado real de la casa: el fin de semana se planifica, con la lista llena interesa
 * cocinar con lo que ya hay, y con la lista vacía interesa llenarla.
 *
 * Devuelve solo el [QuickPromptId] de cada chip (más el número de personas para el menú
 * semanal); la etiqueta y el prompt localizados los pone la capa de presentación. Si no hay
 * sesión, casa o datos, degrada a [staticPrompts] sin fallar nunca: los chips son un extra de
 * la UI, no deben romper la pantalla.
 */
class GetContextualQuickPromptsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val routinesRepository: RoutinesRepository,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository
) {
    suspend operator fun invoke(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        today: LocalDate = LocalDate.now()
    ): List<AiQuickPrompt> {
        val user = authRepository.getCurrentUser() ?: return build(today, null)

        val profile = withTimeoutOrNull(timeoutMs) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }
        val householdId = profile?.activeHouseholdId?.takeIf { it.isNotBlank() }
            ?: return build(today, null)

        // Mismas lecturas que el contexto del chat: independientes → en paralelo, un solo
        // timeout de peor caso en vez de cuatro encadenados.
        return coroutineScope {
            val pendingItems = async {
                withTimeoutOrNull(timeoutMs) {
                    shoppingRepository.observeShoppingList(householdId).firstOrNull()
                }?.count { !it.isChecked }
            }
            val personalRoutines = async {
                withTimeoutOrNull(timeoutMs) {
                    routinesRepository.observePersonalRoutines(user.uid).firstOrNull()
                }.orEmpty()
            }
            val householdRoutines = async {
                withTimeoutOrNull(timeoutMs) {
                    routinesRepository.observeHouseholdRoutines(householdId).firstOrNull()
                }.orEmpty()
            }
            val pantrySize = async {
                withTimeoutOrNull(timeoutMs) {
                    pantryRepository.observePantry(householdId).firstOrNull()
                }?.size ?: 0
            }
            // Nº de personas de la casa: el menú semanal pide cantidades para todas.
            val memberCount = async {
                withTimeoutOrNull(timeoutMs) {
                    householdRepository.observeHousehold(householdId).firstOrNull()
                }?.members?.size ?: 1
            }

            val allRoutines = personalRoutines.await() + householdRoutines.await()
            val pendingRoutinesToday = allRoutines.count { RoutineSchedule.isPendingOn(it, today) }

            build(
                today,
                Snapshot(pendingItems.await(), pendingRoutinesToday, allRoutines.size, pantrySize.await()),
                memberCount.await().coerceAtLeast(1)
            )
        }
    }

    /** Estado de la casa que influye en los chips. Nulo cuando no se pudo leer. */
    private data class Snapshot(
        val pendingItems: Int?,
        val pendingRoutinesToday: Int,
        val totalRoutines: Int,
        val pantrySize: Int
    )

    private fun build(today: LocalDate, snapshot: Snapshot?, memberCount: Int = 1): List<AiQuickPrompt> {
        val contextual = mutableListOf<AiQuickPrompt>()

        // Fin de semana y lunes: es cuando se planifica la semana.
        if (today.dayOfWeek in PLANNING_DAYS) {
            contextual += AiQuickPrompt(QuickPromptId.WEEKLY_MENU, memberCount)
        }

        if (snapshot != null) {
            // Con la despensa llena, lo más útil que sabe hacer el asistente es cocinar con ella.
            if (snapshot.pantrySize >= ENOUGH_PANTRY) {
                contextual += AiQuickPrompt(QuickPromptId.COOK_FROM_PANTRY)
            }

            // Con pocas rutinas, lo útil es ayudar a montarlas: el asistente las crea de una tacada.
            if (snapshot.totalRoutines < FEW_ROUTINES) {
                contextual += AiQuickPrompt(QuickPromptId.CLEANING_PLAN)
            }

            val pendingItems = snapshot.pendingItems
            when {
                pendingItems != null && pendingItems >= MANY_ITEMS ->
                    contextual += AiQuickPrompt(QuickPromptId.RECIPES_FROM_LIST)
                pendingItems == 0 ->
                    contextual += AiQuickPrompt(QuickPromptId.WEEKLY_LIST)
            }

            if (snapshot.pendingRoutinesToday > 0) {
                contextual += AiQuickPrompt(QuickPromptId.ORGANIZE_DAY)
            }
        }

        // Se rellena con los de siempre hasta el tope, sin repetir chip.
        val byId = LinkedHashMap<QuickPromptId, AiQuickPrompt>()
        (contextual + staticPrompts(memberCount)).forEach { byId.putIfAbsent(it.id, it) }
        return byId.values.take(MAX_PROMPTS)
    }

    /**
     * Relleno de siempre. [QuickPromptId.WEEKLY_MENU] va el último a propósito: sigue estando
     * siempre disponible, pero solo sube al primer puesto cuando toca planificar ([PLANNING_DAYS]).
     */
    private fun staticPrompts(memberCount: Int = 1): List<AiQuickPrompt> = listOf(
        AiQuickPrompt(QuickPromptId.QUICK_DINNER),
        AiQuickPrompt(QuickPromptId.ROUTINE_IDEAS),
        AiQuickPrompt(QuickPromptId.CLEANING_TIPS),
        AiQuickPrompt(QuickPromptId.WEEKLY_MENU, memberCount)
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2000L

        /** Máximo de chips en pantalla: más no caben y saturan la vista. */
        const val MAX_PROMPTS = 5

        /** A partir de estos productos pendientes, la lista da para planificar recetas. */
        const val MANY_ITEMS = 8

        /** Por debajo de estas rutinas, se ofrece montar un plan de limpieza de golpe. */
        const val FEW_ROUTINES = 3

        /** A partir de estos productos en casa, la despensa da para proponer recetas. */
        const val ENOUGH_PANTRY = 3

        private val PLANNING_DAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)
    }
}
