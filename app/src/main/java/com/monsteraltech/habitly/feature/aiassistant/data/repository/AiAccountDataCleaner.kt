package com.monsteraltech.habitly.feature.aiassistant.data.repository

import android.content.SharedPreferences
import com.monsteraltech.habitly.feature.aiassistant.data.source.local.AiAssistantDatabase
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.login.domain.account.AccountDataCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Al **borrar la cuenta**, elimina todo rastro local del asistente: el historial de chat (Room)
 * y las prefs (system prompt persistido con el contexto privado de la casa). Los modelos
 * descargados no se tocan: pesan GB y no son datos personales.
 *
 * No corre en el logout normal (ver [AccountDataCleaner]).
 */
class AiAccountDataCleaner @Inject constructor(
    private val database: AiAssistantDatabase,
    private val sharedPreferences: SharedPreferences,
    private val aiAssistantRepository: AiAssistantRepository
) : AccountDataCleaner {

    override suspend fun clearAccountData() {
        // Suelta la Conversation en memoria del engine (lleva el system prompt del usuario
        // saliente). Si el modelo no está cargado puede fallar sin consecuencias.
        runCatching { aiAssistantRepository.resetSession() }
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            // commit y no apply: que el borrado esté en disco antes de dar la cuenta por borrada.
            sharedPreferences.edit().clear().commit()
        }
    }
}
