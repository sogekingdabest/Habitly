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
 * Las acciones que el widget puede ejecutar sin abrir la app: tachar un producto y dar por
 * hecha una rutina.
 *
 * Vive aquí y no en el `ActionCallback` porque el callback corre fuera del ciclo de vida de
 * la app y no puede inyectar nada: solo sabe pedir esta clase al `EntryPoint`. Reutiliza los
 * mismos use cases que las pantallas para que un tick desde el widget y un tick desde la app
 * escriban exactamente lo mismo (incluida la rotación de turnos).
 */
class WidgetActionsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val routinesRepository: RoutinesRepository,
    private val toggleShoppingItemUseCase: ToggleShoppingItemUseCase,
    private val toggleRoutineUseCase: ToggleRoutineUseCase,
    private val advanceRotationUseCase: AdvanceRotationUseCase
) {

    /** Marca un producto de la lista de la compra como comprado. */
    suspend fun checkShoppingItem(itemId: String): Result<Unit> {
        if (itemId.isBlank()) return Result.failure(IllegalArgumentException("itemId vacío"))
        val session = resolveSession() ?: return Result.failure(IllegalStateException("Sin sesión"))
        return toggleShoppingItemUseCase(session.householdId, itemId, true)
    }

    /**
     * Da por hecha una rutina de hoy. Busca la rutina completa porque el use case de toggle
     * necesita la frecuencia y el último completado para recalcular la racha.
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
                // Mismo comportamiento que la pantalla de rutinas: al completarla, el turno
                // pasa al siguiente miembro. Si falla, la rutina ya está marcada igualmente.
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
