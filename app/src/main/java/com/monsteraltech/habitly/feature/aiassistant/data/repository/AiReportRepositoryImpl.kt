package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Saves user feedback reports to the `ai_reports` collection in Firestore.
 */
class AiReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : AiReportRepository {

    override suspend fun reportAssistantMessage(content: String, modelId: String): Result<Unit> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("No active user"))
            val report = mapOf(
                "userId" to uid,
                "modelId" to modelId,
                // Truncate to maximum length enforced by Firestore rules.
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
