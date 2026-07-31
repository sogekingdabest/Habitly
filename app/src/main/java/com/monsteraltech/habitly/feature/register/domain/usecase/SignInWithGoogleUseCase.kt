package com.monsteraltech.habitly.feature.register.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * Completes Google authentication given the idToken the Credential Manager obtained in the
     * presentation layer.
     *
     * Valid for both Google login and registration: Firebase handles internally whether the user is
     * new or existing.
     */
    suspend operator fun invoke(idToken: String): Result<AuthUser> {
        if (idToken.isBlank()) {
            return Result.failure(RegisterError.GoogleSignInFailed)
        }
        return authRepository.signInWithGoogle(idToken)
    }
}
