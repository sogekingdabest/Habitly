package com.monsteraltech.habitly.feature.aiassistant.data.repository

import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiReportRepository

class FakeAiReportRepository : AiReportRepository {

    var shouldFail = false
    val reportedContents = mutableListOf<String>()
    val reportedModelIds = mutableListOf<String>()

    override suspend fun reportAssistantMessage(content: String, modelId: String): Result<Unit> {
        return if (shouldFail) {
            Result.failure(Exception("Error simulado"))
        } else {
            reportedContents.add(content)
            reportedModelIds.add(modelId)
            Result.success(Unit)
        }
    }
}
