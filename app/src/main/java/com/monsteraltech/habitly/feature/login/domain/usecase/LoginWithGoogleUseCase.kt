package com.monsteraltech.habitly.feature.login.domain.usecase

import com.monsteraltech.habitly.feature.login.domain.repository.AuthRepository
import com.monsteraltech.habitly.feature.register.domain.model.AuthUser
import javax.inject.Inject

class LoginWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<AuthUser> {
        return repository.signInWithGoogle(idToken)
    }
}
