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
 * Creates AI-suggested routines in the active household.
 *
 * @return Number of routines created, or [Result.failure] if session/household is unavailable or all fail.
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
            ?: return Result.failure(IllegalStateException("User unauthenticated"))

        val profile = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
            householdRepository.observeUserProfile(user.uid).firstOrNull()
        }
        val householdId = profile?.activeHouseholdId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No active household"))

        var created = 0
        var lastError: Throwable? = null

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
