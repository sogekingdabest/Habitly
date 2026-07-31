package com.monsteraltech.habitly.feature.login.domain.account

/**
 * Cleanup of account-bound local data (databases, preferences…) on **account deletion**. Each
 * feature with sensitive local state contributes its implementation through Hilt multibinding
 * (`@Binds @IntoSet`), and AuthRepositoryImpl runs them all, so no account deletion can forget a
 * cleanup.
 *
 * NOTE: this does NOT run on an ordinary logout. A logout (voluntary or from Firebase session
 * expiry) must not take the history with it: the user would sign back in and find it empty. On
 * uninstall, Android wipes all internal storage on its own anyway.
 */
interface AccountDataCleaner {
    suspend fun clearAccountData()
}
