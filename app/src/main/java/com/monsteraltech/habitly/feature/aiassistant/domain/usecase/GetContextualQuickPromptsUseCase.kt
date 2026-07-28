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
 * Decides which suggestion chips appear above the chat input, from the moment and the household's
 * actual state: weekends are for planning, a full pantry calls for cooking with what is there, and
 * an empty list calls for filling it.
 *
 * It returns only each chip's [QuickPromptId] (plus the member count for the weekly menu); the
 * localised label and prompt are the presentation layer's job. With no session, household or data
 * it degrades to [staticPrompts] and never fails: the chips are a UI extra and must not take the
 * screen down with them.
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

        // Same reads as the chat context: independent, so run them in parallel for a single
        // worst-case timeout instead of four chained ones.
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
            // Household size: the weekly menu asks for quantities for everyone.
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

    /** The household state that drives the chips. Null when it could not be read. */
    private data class Snapshot(
        val pendingItems: Int?,
        val pendingRoutinesToday: Int,
        val totalRoutines: Int,
        val pantrySize: Int
    )

    private fun build(today: LocalDate, snapshot: Snapshot?, memberCount: Int = 1): List<AiQuickPrompt> {
        val contextual = mutableListOf<AiQuickPrompt>()

        // Weekend and Monday: when people plan the week.
        if (today.dayOfWeek in PLANNING_DAYS) {
            contextual += AiQuickPrompt(QuickPromptId.WEEKLY_MENU, memberCount)
        }

        if (snapshot != null) {
            // With a full pantry, the most useful thing the assistant does is cook from it.
            if (snapshot.pantrySize >= ENOUGH_PANTRY) {
                contextual += AiQuickPrompt(QuickPromptId.COOK_FROM_PANTRY)
            }

            // With few routines, the useful thing is help building them: the assistant creates a
            // whole set in one go.
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

        // Padded with the standard ones up to the cap, without repeating a chip.
        val byId = LinkedHashMap<QuickPromptId, AiQuickPrompt>()
        (contextual + staticPrompts(memberCount)).forEach { byId.putIfAbsent(it.id, it) }
        return byId.values.take(MAX_PROMPTS)
    }

    /**
     * The standard padding. [QuickPromptId.WEEKLY_MENU] comes last on purpose: it stays always
     * available, but only climbs to first place on the planning days ([PLANNING_DAYS]).
     */
    private fun staticPrompts(memberCount: Int = 1): List<AiQuickPrompt> = listOf(
        AiQuickPrompt(QuickPromptId.QUICK_DINNER),
        AiQuickPrompt(QuickPromptId.ROUTINE_IDEAS),
        AiQuickPrompt(QuickPromptId.CLEANING_TIPS),
        AiQuickPrompt(QuickPromptId.WEEKLY_MENU, memberCount)
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2000L

        /** Cap on chips shown: more do not fit and clutter the view. */
        const val MAX_PROMPTS = 5

        /** From this many pending items on, the list is enough to plan recipes around. */
        const val MANY_ITEMS = 8

        /** Below this many routines, offer to build a cleaning plan in one go. */
        const val FEW_ROUTINES = 3

        /** From this many items at home on, the pantry is enough to suggest recipes from. */
        const val ENOUGH_PANTRY = 3

        private val PLANNING_DAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)
    }
}
