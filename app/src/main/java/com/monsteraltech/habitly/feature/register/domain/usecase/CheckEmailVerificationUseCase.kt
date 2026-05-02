package com.monsteraltech.habitly.feature.register.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import javax.inject.Inject

class CheckEmailVerificationUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * Recarga el FirebaseUser y retorna si el email ya está verificado.
     * Usado por EmailVerificationViewModel para el polling periódico.
     */
    suspend operator fun invoke(): Result<Boolean> {
        return authRepository.reloadCurrentUser().map { authUser ->
            authUser.isEmailVerified
        }
    }
}
