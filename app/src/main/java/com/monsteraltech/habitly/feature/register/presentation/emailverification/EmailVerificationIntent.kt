package com.monsteraltech.habitly.feature.register.presentation.emailverification

sealed class EmailVerificationIntent {
    object CheckVerificationClicked : EmailVerificationIntent()
    object ResendEmailClicked : EmailVerificationIntent()
    object CancelClicked : EmailVerificationIntent()
}
