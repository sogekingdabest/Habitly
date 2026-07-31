package com.monsteraltech.habitly.feature.widget.domain

import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.repository.RoutinesRepository
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import com.monsteraltech.habitly.feature.shopping.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds the [WidgetSnapshot] by reading the user's active household: pending products and today's
 * uncompleted routines. It leans on Firestore's offline cache, so it works even with no network.
 */
class BuildWidgetSnapshotUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val shoppingRepository: ShoppingRepository,
    private val routinesRepository: RoutinesRepository
) {
    suspend operator fun invoke(): WidgetSnapshot {
        val user = authRepository.getCurrentUser()
            ?: return WidgetSnapshot(state = WidgetState.NO_SESSION)

        val profile = withTimeoutOrNull(TIMEOUT_MS) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }
        val householdId = profile?.activeHouseholdId?.takeIf { it.isNotBlank() }
            ?: return WidgetSnapshot(state = WidgetState.NO_HOUSEHOLD)

        val shopping = withTimeoutOrNull(TIMEOUT_MS) {
            shoppingRepository.observeShoppingList(householdId).firstOrNull()
        }.orEmpty()
        val personal = withTimeoutOrNull(TIMEOUT_MS) {
            routinesRepository.observePersonalRoutines(user.uid).firstOrNull()
        }.orEmpty()
        val household = withTimeoutOrNull(TIMEOUT_MS) {
            routinesRepository.observeHouseholdRoutines(householdId).firstOrNull()
        }.orEmpty()

        val pendingItems = shopping
            .filter { !it.isChecked }
            .map { WidgetLine(id = it.id, label = it.name) }

        val today = LocalDate.now()
        val pendingRoutines = (personal + household)
            .filter { RoutineSchedule.isPendingOn(it, today) }
            .map { WidgetLine(id = it.id, label = it.title) }

        return WidgetSnapshot(
            state = WidgetState.READY,
            pendingItems = pendingItems,
            pendingRoutines = pendingRoutines
        )
    }

    private companion object {
        const val TIMEOUT_MS = 3000L
    }
}
