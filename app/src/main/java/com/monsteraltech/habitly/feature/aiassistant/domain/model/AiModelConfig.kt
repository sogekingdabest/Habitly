package com.monsteraltech.habitly.feature.aiassistant.domain.model

data class AiModelConfig(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val filename: String,
    /**
     * SHA-256 (hex) del fichero publicado, para verificar la descarga. Es obligatorio:
     * descargamos gigabytes que van directos a un motor de inferencia nativo, así que el
     * tamaño no basta como validación. `AiModelCatalogTest` rompe la build si falta.
     *
     * Se saca del puntero LFS de Hugging Face SIN descargar el modelo:
     *
     *     curl -sS "https://huggingface.co/<repo>/raw/main/<fichero>.litertlm"
     *
     * devuelve `oid sha256:<hash>` y `size <bytes>`, que son justo este campo y [sizeBytes].
     */
    val sha256: String,
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
    // sizeBytes y sha256 salen del puntero LFS del repo de Hugging Face (ver KDoc de
    // AiModelConfig.sha256): son los valores exactos del artefacto publicado, no estimaciones.
    val Gemma4_E2B_IT = AiModelConfig(
        id = "gemma-4-e2b",
        name = "Gemma 4 (2B) - Inteligente",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L, // 2.59 GB
        filename = "gemma-4-e2b.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    )

    val Gemma4_E4B_IT = AiModelConfig(
        id = "gemma-4-e4b",
        name = "Gemma 4 (4B) - Muy Potente",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L, // 3.66 GB
        filename = "gemma-4-e4b.litertlm",
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
    )

    val models = listOf(Gemma4_E2B_IT, Gemma4_E4B_IT)
}
