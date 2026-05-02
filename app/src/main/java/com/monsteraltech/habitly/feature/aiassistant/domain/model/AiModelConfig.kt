package com.monsteraltech.habitly.feature.aiassistant.domain.model

data class AiModelConfig(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val filename: String
)

object AvailableAiModels {
    val Qwen2_5_1_5B = AiModelConfig(
        id = "qwen2.5-1.5b",
        name = "Qwen 2.5 (1.5B) - Equilibrado",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 1_600_000_000L, // 1.6 GB
        filename = "qwen2.5-1.5b.litertlm"
    )

    val Gemma4_E2B_IT = AiModelConfig(
        id = "gemma-4-e2b",
        name = "Gemma 4 (2B) - Inteligente",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580_000_000L, // 2.58 GB
        filename = "gemma-4-e2b.litertlm"
    )

    val Gemma4_E4B_IT = AiModelConfig(
        id = "gemma-4-e4b",
        name = "Gemma 4 (4B) - Muy Potente",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L, // 3.65 GB
        filename = "gemma-4-e4b.litertlm"
    )

    val models = listOf(Qwen2_5_1_5B, Gemma4_E2B_IT, Gemma4_E4B_IT)
}
