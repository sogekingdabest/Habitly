package com.monsteraltech.habitly.feature.aiassistant.domain.usecase

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.household.domain.repository.HouseholdRepository
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.domain.usecase.AddRoutineUseCase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Crea en la casa activa las rutinas que ha propuesto la IA.
 * Resuelve internamente el usuario y su casa para no acoplar el ViewModel a Firebase.
 *
 * Las rutinas nacen sin recordatorio: ponerle hora a cinco rutinas de golpe sin preguntar
 * sería invasivo, y el usuario puede añadirlo al editarlas.
 *
 * @return número de rutinas creadas, o [Result.failure] si no hay sesión/casa o fallan todas.
 */
class AddAiRoutinesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val addRoutineUseCase: AddRoutineUseCase
) {
    suspend operator fun invoke(
        routines: List<AiRoutineSuggestion>,
        type: RoutineType
    ): Result<Int> {
        if (routines.isEmpty()) return Result.success(0)

        val user = authRepository.getCurrentUser()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado"))

        val profile = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }
        val householdId = profile?.activeHouseholdId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No hay una casa activa"))

        var created = 0
        var lastError: Throwable? = null

        // No hay batch para rutinas (personales y de casa viven en colecciones distintas),
        // así que se crean una a una y se informa de cuántas entraron.
        for (suggestion in routines) {
            addRoutineUseCase(
                userId = user.uid,
                householdId = householdId,
                title = suggestion.title,
                description = suggestion.description,
                type = type,
                frequency = suggestion.frequency,
                scheduledDays = suggestion.scheduledDays,
                reminderTime = null,
                intervalDays = suggestion.intervalDays
            ).fold(
                onSuccess = { created++ },
                onFailure = { lastError = it }
            )
        }

        // Solo es un fallo si no entró ninguna: con algunas creadas, informamos del número.
        return if (created == 0 && lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(created)
        }
    }

    private companion object {
        const val PROFILE_TIMEOUT_MS = 3000L
    }
}
