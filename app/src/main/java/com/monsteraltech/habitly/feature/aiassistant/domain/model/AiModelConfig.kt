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
    val supportsGpu: Boolean = true,
    /**
     * The model emits a reasoning channel unless told otherwise, so every turn is sent with
     * `ThinkingConfig(enableThinking = false)`. We never want it here: the reasoning is invisible
     * to the user, it competes with the answer for the same [maxTokens] window, and it delays the
     * first visible token.
     *
     * `false` means "say nothing about thinking", not "thinking off": a null `ThinkingConfig`
     * leaves whatever the model's own metadata declares, which is the right default for a model
     * that does not reason.
     */
    val thinksByDefault: Boolean = false,
    /**
     * Multiplicative repetition penalty (HuggingFace style; 1.0 is no penalty) for the turns that
     * generate prose. `null` leaves the engine's default.
     *
     * Deliberately **not** applied to the extraction turns: there the schema already fixes the
     * shape, and penalising a token for having appeared would push the model away from values it
     * is supposed to repeat — three products in a row measured in `unidad` is the correct answer,
     * not a repetition to discourage.
     */
    val repetitionPenalty: Float? = null
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

    /**
     * The model for modest devices (4 GB of RAM, where Gemma 4 is out of the question).
     * LFM2.5 lists Spanish among its 8 languages and its model card recommends it for agentic
     * tasks, data extraction and RAG, which is exactly what this app does.
     *
     * Two differences from Gemma, both from its model card:
     *  - **CPU only**: the hybrid conv graph is not supported by current GPU delegates.
     *  - Its function calling uses a Python-like format between special tokens, not the one our
     *    `tool(...)` path expects, so it goes through the JSON extractor.
     */
    val LFM2_5_1_2B_Instruct = AiModelConfig(
        id = "lfm2-5-1-2b",
        name = "LFM2.5 (1.2B) - Ligero",
        downloadUrl = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm",
        // Resubido por litert-community: el 2026-11-04 el puntero LFS pasó a estos valores y los
        // anteriores (735_999_360 / e51f10c7...) rompían la descarga. El fallo era de los malos:
        // se bajaban los 736 MB enteros, verifyChecksum lanzaba NonRetryableDownloadException al
        // final y el .tmp se borraba, así que el modelo de los móviles de 4 GB no había forma de
        // instalarlo. Conviene revalidar estos dos campos cuando se toque el catálogo.
        sizeBytes = 736_015_744L, // 736 MB
        filename = "lfm2-5-1-2b-int4.litertlm",
        sha256 = "e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b",
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

    /**
     * The recommended model. Smaller than Gemma 4 E2B and far smaller than the E4B it replaces,
     * while scoring higher on the only benchmarks this app lives on: BFCL V4 71.39, ToolSandbox
     * 80.36, AutomationBench 41.29. It loses on general knowledge (GPQA Diamond), which Habitly
     * never asks a model for.
     *
     * The `mixed_int4_ekv4096` variant: 4096 is the window the app needs, and the manifest
     * recommends it even on an 8 GB phone (a Pixel 8a decodes it at 15.13 tok/s against 12.77 for
     * the 2048 file — here the bigger window is also the faster one).
     *
     * **Needs LiteRT-LM 0.18.1 or newer** (`min_runtime_version` in the manifest); we ship 0.19.0.
     * On 0.14.0 the file simply does not load.
     *
     * Licence: LFM Open License v1.0, whose section 5 excludes commercial use by organisations
     * with 10 M USD or more of annual revenue. Already accepted for LFM2.5, so nothing changes —
     * but it is not the Gemma Terms, and that is worth knowing before this ships anywhere else.
     */
    val LFM3_1_7B = AiModelConfig(
        id = "lfm3-1-7b",
        name = "LFM3 (1.7B) - Recomendado",
        downloadUrl = "https://huggingface.co/litert-community/LFM3-1.7B-LiteRT/resolve/main/LFM3-1.7B_mixed_int4_ekv4096.litertlm",
        sizeBytes = 1_441_629_120L, // 1.44 GB
        filename = "lfm3-1-7b-int4.litertlm",
        sha256 = "327486bf01d0b1eefd727b7aadec8627a2b8db143349262857ac98403109d72b",
        // **Measured in the app**, which is the only number that counts: on a Galaxy S25+ running
        // on GPU, 3.09 GB PSS / 3.15 GB RSS, of which 2.39 GB is `GL mtrack` (the weights on the
        // GPU). Worth knowing why that is 3x the 0.91 GB its manifest reports: the manifest
        // measures the runtime alone at a 2048 window, and we run the whole app at 4096.
        //
        // The gate also has to survive the CPU fallback, which the manifest puts at 2.27-2.68 GB
        // for the runtime alone. So 6 GB is tight-but-usable rather than generous, which is
        // exactly what `Tight` says, and the 2.3x-of-file rule (3.3 GB) would have been wrong.
        minRamBytes = 6 * GB,
        recommendedRamBytes = 8 * GB,
        maxTokens = 4096, // The ekv4096 file; asking for more than the file's own window is not an option.
        // The file itself declares TOP_P with k 50, p 1 and **temperature 0.1**, which is the model
        // card's "General" setting. Our chat default of 0.9 is nine times that: overriding a
        // model's own declared sampler that hard is how a good model gets a bad reputation.
        defaultTemperature = 0.1,
        supportsSpeculativeDecoding = false, // No drafter section in the file.
        // Its tool calls come back as text in LFM3's own XML (`<tool_call><function=...>`), which
        // LiteRT-LM does not parse. So it goes through the JSON extractor — which since the
        // ResponseFormat work is the stronger path anyway, not the consolation prize it used to be.
        supportsToolCalling = false,
        // `default_backend: gpu`, and the export is deliberately plain (no GPU composites, state
        // ping-ponged on the GPU between calls). This is what separates it from LFM2.5.
        supportsGpu = true,
        // "Thinks by default (enable_thinking false turns it off)", says the manifest. Its metadata
        // also declares the channel (`thought`, delimited by <think> and </think>), so under the
        // runtime's default channel configuration any reasoning that still leaks lands in
        // `Message.channels` instead of the text we stream — `Message.toString()` returns only
        // `contents`. Belt and braces, because the card warns that the int4 variants "do not
        // always come out clean" with thinking off.
        thinksByDefault = true,
        repetitionPenalty = 1.1f // The model card's figure, which goes with its temperature of 0.1.
    )

    /**
     * Ordered from least to most capable: the dropdown shows first what a modest phone can
     * actually use, and `bestModelForDevice` picks the **last** entry the device supports, so this
     * order is what makes [LFM3_1_7B] the default on any phone that can take it.
     *
     * Gemma 4 E4B (3.66 GB, 8 GB of RAM) was retired here: LFM3 is 2.5x smaller, asks for less
     * memory and scores higher at tool calling and extraction, which is all this app ever does
     * with a model. Retiring it needed no migration code — `LocalModelManager.cleanupOrphanedModels`
     * runs over this list and reclaims the 3.66 GB from whoever had downloaded it, and a stored
     * `selected_model_id` that no longer resolves falls back to the best model for the device.
     * Note **when** that sweep happens: it is in the repository's `init`, so the space comes back
     * the first time the assistant is opened, not when the app starts. Gemma 4 E2B stays because
     * it is the only entry left with native function calling.
     */
    val models = listOf(LFM2_5_1_2B_Instruct, Gemma4_E2B_IT, LFM3_1_7B)
}
