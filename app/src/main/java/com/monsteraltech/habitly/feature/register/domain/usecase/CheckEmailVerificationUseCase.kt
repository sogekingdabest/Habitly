package com.monsteraltech.habitly.feature.register.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import javax.inject.Inject

class CheckEmailVerificationUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * Reloads the FirebaseUser and reports whether the email is verified yet. Used by
     * EmailVerificationViewModel for its periodic polling.
     */
    suspend operator fun invoke(): Result<Boolean> {
        return authRepository.reloadCurrentUser().map { authUser ->
            authUser.isEmailVerified
        }
    }
}
