package com.monsteraltech.habitly.feature.register.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * Completa la autenticación con Google dado el idToken obtenido
     * por el Credential Manager en la capa de Presentation.
     *
     * Válido tanto para Login como para Registro con Google:
     * Firebase gestiona internamente si el usuario es nuevo o existente.
     */
    suspend operator fun invoke(idToken: String): Result<AuthUser> {
        if (idToken.isBlank()) {
            return Result.failure(RegisterError.GoogleSignInFailed)
        }
        return authRepository.signInWithGoogle(idToken)
    }
}
