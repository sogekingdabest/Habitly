package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Guarda el reporte en la colección `ai_reports` de Firestore (solo creación; la lectura queda
 * para la consola del desarrollador). Es el único punto donde una respuesta del asistente sale
 * del dispositivo, y siempre a petición explícita del usuario.
 */
class AiReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : AiReportRepository {

    override suspend fun reportAssistantMessage(content: String, modelId: String): Result<Unit> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("No hay usuario activo"))
            val report = mapOf(
                "userId" to uid,
                "modelId" to modelId,
                // Tope alineado con la validación de las reglas de Firestore.
                "content" to content.take(MAX_CONTENT_CHARS),
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("ai_reports").add(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MAX_CONTENT_CHARS = 8000
    }
}
