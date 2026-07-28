package com.monsteraltech.habitly.feature.aiassistant.domain.model

data class AiModelConfig(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val filename: String,
    /**
     * SHA-256 (hex) of the published file, used to verify the download. Mandatory: we fetch
     * gigabytes that go straight into a native inference engine, so size alone is not validation.
     * `AiModelCatalogTest` breaks the build if it is missing.
     *
     * It can be read from the Hugging Face LFS pointer **without** downloading the model:
     *
     *     curl -sS "https://huggingface.co/<repo>/raw/main/<file>.litertlm"
     *
     * returns `oid sha256:<hash>` and `size <bytes>`, which are exactly this field and [sizeBytes].
     */
    val sha256: String,
    /**
     * Minimum RAM (bytes, in marketing GB) below which the model is not offered at all — neither
     * download nor load. Peak memory for these engines runs at roughly 2.3x the file size, and
     * exceeding it does not raise an exception: it kills the process.
     */
    val minRamBytes: Long,
    /** RAM above which the model runs comfortably. Between [minRamBytes] and this, we warn. */
    val recommendedRamBytes: Long,
    /** Token cap (context plus generation) in the EngineConfig. Tunable per model. */
    val maxTokens: Int = 4096,
    /** Temperature of the conversational turn; the extraction turn uses its own, lower one. */
    val defaultTemperature: Double = 0.9,
    /** The artifact ships an MTP drafter (speculative decoding); only pays off on GPU. */
    val supportsSpeculativeDecoding: Boolean = true,
    /** The model does function calling reliably: use the tool extractor rather than the JSON one. */
    val supportsToolCalling: Boolean = true,
    /**
     * The model's graph runs on the GPU delegates. When `false` the load goes **straight** to CPU
     * without trying GPU: a delegate choking on a graph it does not support can go down with
     * SIGSEGV, which the fallback's `catch` never sees.
     */
    val supportsGpu: Boolean = true
)

private const val GB = 1_073_741_824L

object AvailableAiModels {
    // sizeBytes and sha256 come from the Hugging Face LFS pointer (see the KDoc on
    // AiModelConfig.sha256): the exact values of the published artifact, not estimates.
    val Gemma4_E2B_IT = AiModelConfig(
        id = "gemma-4-e2b",
        name = "Gemma 4 (2B) - Inteligente",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L, // 2.59 GB
        filename = "gemma-4-e2b.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        minRamBytes = 6 * GB,
        recommendedRamBytes = 8 * GB
    )

    val Gemma4_E4B_IT = AiModelConfig(
        id = "gemma-4-e4b",
        name = "Gemma 4 (4B) - Muy Potente",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L, // 3.66 GB
        filename = "gemma-4-e4b.litertlm",
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        minRamBytes = 8 * GB,
        recommendedRamBytes = 12 * GB
    )

    /**
     * The model for modest devices (4 GB of RAM, where the Gemma 4 family is out of the question).
     * LFM2.5 lists Spanish among its 8 languages and its model card recommends it for agentic
     * tasks, data extraction and RAG, which is exactly what this app does.
     *
     * Two differences from the Gemmas, both from its model card:
     *  - **CPU only**: the hybrid conv graph is not supported by current GPU delegates.
     *  - Its function calling uses a Python-like format between special tokens, not the one our
     *    `tool(...)` path expects, so it goes through the JSON extractor.
     */
    val LFM2_5_1_2B_Instruct = AiModelConfig(
        id = "lfm2-5-1-2b",
        name = "LFM2.5 (1.2B) - Ligero",
        downloadUrl = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm",
        sizeBytes = 735_999_360L, // 736 MB
        filename = "lfm2-5-1-2b-int4.litertlm",
        sha256 = "e51f10c7b3b8e0568148484e2ece6473f4195ea5a3111b333fdaf9496434fe05",
        // **Measured** thresholds, not estimates: with the 4096 window the app reaches 2.10 GB RSS
        // (1.04 GB private dirty and unreclaimable; the rest are clean file pages the kernel can
        // evict). That fits on a 4 GB phone, but only just — hence 6 GB as the recommendation and
        // a warning rather than a green light at 4.
        minRamBytes = 4 * GB,
        recommendedRamBytes = 6 * GB,
        // 4096, like the others. 2048 was tried to save RAM and was a mistake: the app's system
        // prompt already takes ~650 tokens and a weekly menu around 1200, so a single exchange
        // exhausted the window and the second message's prefill would start failing. Doubling it
        // costs a measured 240 MB — LFM2's KV cache is not as cheap as its model card suggests —
        // but without it there is no use case.
        maxTokens = 4096,
        supportsSpeculativeDecoding = false,
        supportsToolCalling = false,
        supportsGpu = false
    )

    // Ordered from least to most demanding, so in the dropdown what a modest phone can actually
    // use comes first instead of sitting below two blocked models.
    val models = listOf(LFM2_5_1_2B_Instruct, Gemma4_E2B_IT, Gemma4_E4B_IT)
}
