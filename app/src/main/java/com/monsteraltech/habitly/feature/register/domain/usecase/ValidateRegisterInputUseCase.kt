package com.monsteraltech.habitly.feature.register.domain.usecase

import android.util.Patterns
import com.monsteraltech.habitly.feature.register.domain.model.RegisterCredentials
import com.monsteraltech.habitly.feature.register.domain.model.RegisterError
import javax.inject.Inject

class ValidateRegisterInputUseCase @Inject constructor() {

    /**
     * Validates every field of the registration form. Returns an empty list when all is valid.
     * Never throws.
     */
    operator fun invoke(credentials: RegisterCredentials): List<RegisterError> {
        val errors = mutableListOf<RegisterError>()

        // — Email —
        if (credentials.email.isBlank()) {
            errors.add(RegisterError.EmailBlank)
        } else if (!Patterns.EMAIL_ADDRESS.matcher(credentials.email).matches()) {
            errors.add(RegisterError.EmailInvalidFormat)
        }

        // — Password —
        if (credentials.password.length < 8) {
            errors.add(RegisterError.PasswordTooShort)
        }
        if (credentials.password.none { it.isUpperCase() }) {
            errors.add(RegisterError.PasswordNoUppercase)
        }
        if (credentials.password.none { it.isDigit() }) {
            errors.add(RegisterError.PasswordNoDigit)
        }

        // — Confirm password —
        if (credentials.password != credentials.confirmPassword) {
            errors.add(RegisterError.PasswordsDoNotMatch)
        }

        // — DisplayName —
        if (credentials.displayName.isBlank()) {
            errors.add(RegisterError.DisplayNameBlank)
        } else if (credentials.displayName.trim().length < 2) {
            errors.add(RegisterError.DisplayNameTooShort)
        }

        return errors
    }
}
