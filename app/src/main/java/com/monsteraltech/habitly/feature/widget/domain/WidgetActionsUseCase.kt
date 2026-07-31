package com.monsteraltech.habitly.feature.widget.domain

import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.usecase.AdvanceRotationUseCase
import com.monsteraltech.habitly.feature.routines.domain.usecase.ToggleRoutineUseCase
import com.monsteraltech.habitly.feature.shopping.domain.usecase.ToggleShoppingItemUseCase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The actions the widget can run without opening the app: ticking off a product and marking a
 * routine done.
 *
 * It lives here rather than in the `ActionCallback` because the callback runs outside the app's
 * lifecycle and can inject nothing: all it can do is ask the `EntryPoint` for this class. It reuses
 * the same use cases as the screens, so a tick from the widget and a tick from the app write exactly
 * the same thing (turn rotation included).
 */
class WidgetActionsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val routinesRepository: RoutinesRepository,
    private val toggleShoppingItemUseCase: ToggleShoppingItemUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val advanceRotationUseCase: AdvanceRotationUseCase
) {

    /** Marks a shopping-list product as bought. */
    suspend fun checkShoppingItem(itemId: String): Result<Unit> {
        if (itemId.isBlank()) return Result.failure(IllegalArgumentException("itemId vacío"))
        val session = resolveSession() ?: return Result.failure(IllegalStateException("Sin sesión"))
        return toggleShoppingItemUseCase(session.householdId, itemId, true)
    }

    /**
     * Marks one of today's routines done. It looks up the full routine because the toggle use case
     * needs the frequency and the last completion to recompute the streak.
     */
    suspend fun completeRoutine(routineId: String): Result<Unit> {
        if (routineId.isBlank()) return Result.failure(IllegalArgumentException("routineId vacío"))
        val session = resolveSession() ?: return Result.failure(IllegalStateException("Sin sesión"))

        val personal = withTimeoutOrNull(TIMEOUT_MS) {
            routinesRepository.observePersonalRoutines(session.userId).firstOrNull()
        }.orEmpty()
        val household = withTimeoutOrNull(TIMEOUT_MS) {
            routinesRepository.observeHouseholdRoutines(session.householdId).firstOrNull()
        }.orEmpty()

        val routine = (personal + household).find { it.id == routineId }
            ?: return Result.failure(IllegalStateException("Rutina no encontrada"))

        return toggleRoutineUseCase(session.userId, session.householdId, routine, true)
            .onSuccess {
                // Same behaviour as the routines screen: completing it passes the turn to the next
                // member. If that fails, the routine is marked done anyway.
                val members = withTimeoutOrNull(TIMEOUT_MS) {
                    householdRepository.observeHousehold(session.householdId).firstOrNull()
                }?.members.orEmpty()
                advanceRotationUseCase(session.userId, session.householdId, routine, members)
            }
    }

    private suspend fun resolveSession(): Session? {
        val user = authRepository.getCurrentUser() ?: return null
        val householdId = withTimeoutOrNull(TIMEOUT_MS) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }?.activeHouseholdId?.takeIf { it.isNotBlank() } ?: return null
        return Session(userId = user.uid, householdId = householdId)
    }

    private data class Session(val userId: String, val householdId: String)

    private companion object {
        const val TIMEOUT_MS = 5000L
    }
}
