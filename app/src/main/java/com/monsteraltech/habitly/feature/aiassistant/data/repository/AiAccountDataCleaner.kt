package com.monsteraltech.habitly.feature.aiassistant.data.repository

import android.content.SharedPreferences
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiAssistantDatabase
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.login.domain.account.AccountDataCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Clears local AI data (Room chat history and preferences) upon account deletion.
 * Downloaded model binaries are retained as they contain no user data.
 */
class AiAccountDataCleaner @Inject constructor(
    private val database: AiAssistantDatabase,
    private val sharedPreferences: SharedPreferences,
    private val aiAssistantRepository: AiAssistantRepository
) : AccountDataCleaner {

    override suspend fun clearAccountData() {
        // Release active in-memory session.
        runCatching { aiAssistantRepository.resetSession() }
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            // Commit synchronously to disk before finishing account deletion.
            sharedPreferences.edit().clear().commit()
        }
    }
}
