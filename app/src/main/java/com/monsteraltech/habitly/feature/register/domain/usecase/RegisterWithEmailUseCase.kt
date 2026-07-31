package com.monsteraltech.habitly.feature.register.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials
import javax.inject.Inject

class RegisterWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val validateInput: ValidateRegisterInputUseCase
) {

    /**
     * Orchestrates the registration flow:
     * 1. Validates the credentials. On errors, fails with the first.
     * 2. Delegates to the repository.
     * 3. Propagates the Result<AuthUser> untransformed.
     */
    suspend operator fun invoke(credentials: RegisterCredentials): Result<AuthUser> {
        val errors = validateInput(credentials)
        if (errors.isNotEmpty()) {
            return Result.failure(errors.first())
        }
        return authRepository.register(credentials)
    }
}
