package com.monsteraltech.habitly.feature.login.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Valida el formato del correo y solicita el envío del enlace de restablecimiento. La validación
 * vive en domain (regex pura, sin `android.util.Patterns`) para poder testearla en la JVM.
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
