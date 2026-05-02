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
     * Orquesta el flujo de registro:
     * 1. Valida las credenciales. Si hay errores, falla con el primero.
     * 2. Delega al repositorio.
     * 3. Propaga el Result<AuthUser> sin transformar.
     */
    suspend operator fun invoke(credentials: RegisterCredentials): Result<AuthUser> {
        val errors = validateInput(credentials)
        if (errors.isNotEmpty()) {
            return Result.failure(errors.first())
        }
        return authRepository.register(credentials)
    }
}
