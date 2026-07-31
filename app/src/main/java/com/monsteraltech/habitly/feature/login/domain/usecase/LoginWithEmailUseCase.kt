package com.monsteraltech.habitly.feature.login.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.model.AuthToken
import com.monsteraltech.habitly.feature.login.domain.model.LoginCredentials
import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import javax.inject.Inject

class LoginWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthToken> {
        if (!EMAIL_REGEX.matches(email)) {
            return Result.failure(IllegalArgumentException("Formato de email inválido"))
        }

        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener al menos 6 caracteres"))
        }

        return authRepository.login(LoginCredentials(email = email, password = password))
    }

    companion object {
        // A pure-domain regex (no android.util.Patterns), so the domain layer is testable in JVM
        // tests and does not depend on the Android framework.
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
