package com.monsteraltech.habitly.feature.login.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Validates the email format and requests the reset link. The validation lives in domain (a pure
 * regex, no `android.util.Patterns`) so it is testable on the JVM.
 */
class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        val trimmed = email.trim()
        if (!EMAIL_REGEX.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Formato de email inválido"))
        }
        return authRepository.sendPasswordResetEmail(trimmed)
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
