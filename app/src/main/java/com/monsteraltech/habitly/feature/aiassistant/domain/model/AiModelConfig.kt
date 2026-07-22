package com.monsteraltech.habitly.feature.aiassistant.domain.model

data class AiModelConfig(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val filename: String,
    /**
     * SHA-256 (hex) del fichero publicado, para verificar la descarga. Nulo = sin pin: el
     * gestor loguea el hash calculado al terminar una descarga; cópialo aquí (o desde los
     * metadatos LFS de Hugging Face) para activar la verificación estricta.
     */
    val sha256: String? = null,
    /** Tope de tokens (contexto + generación) del EngineConfig. Ajustable por modelo. */
    val maxTokens: Int = 4096,
    /** Temperatura del turno conversacional (el turno de extracción usa la suya, más baja). */
    val defaultTemperature: Double = 0.9,
    /** El artefacto trae drafter MTP (speculative decoding); solo aporta en GPU. */
    val supportsSpeculativeDecoding: Boolean = true,
    /** El modelo hace function-calling con fiabilidad: usa el extractor por tools en vez del de JSON. */
    val supportsToolCalling: Boolean = true
)

object AvailableAiModels {
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

    val models = listOf(Gemma4_E2B_IT, Gemma4_E4B_IT)
}
