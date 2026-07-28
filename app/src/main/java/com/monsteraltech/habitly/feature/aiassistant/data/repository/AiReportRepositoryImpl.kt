package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Stores the report in Firestore's `ai_reports` collection — creation only; reading it is the
 * developer console's job. This is the only point where an assistant answer leaves the device, and
 * always at the user's explicit request.
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
                // Cap kept in step with the Firestore rules' validation.
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
