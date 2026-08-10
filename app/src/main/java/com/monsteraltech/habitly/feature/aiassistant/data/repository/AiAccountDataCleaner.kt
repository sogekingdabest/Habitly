package com.monsteraltech.habitly.feature.aiassistant.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiAssistantDatabase
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.login.domain.account.AccountDataCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * On **account deletion**, wipes every local trace of the assistant: the chat history in Room and
 * the prefs (the persisted system prompt, which carries the household's private context). Downloaded
 * models are left alone — they weigh gigabytes and are not personal data.
 *
 * This does not run on an ordinary logout (see [AccountDataCleaner]).
 */
class AiAccountDataCleaner @Inject constructor(
    private val database: AiAssistantDatabase,
    private val sharedPreferences: SharedPreferences,
    private val aiAssistantRepository: AiAssistantRepository
) : AccountDataCleaner {

    override suspend fun clearAccountData() {
        // Releases the engine's in-memory Conversation, which holds the departing user's system
        // prompt. Harmless if it fails because no model is loaded.
        runCatching { aiAssistantRepository.resetSession() }
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            // commit, not apply: the deletion must reach disk before the account counts as deleted.
            sharedPreferences.edit(commit = true) { clear() }
        }
    }
}
