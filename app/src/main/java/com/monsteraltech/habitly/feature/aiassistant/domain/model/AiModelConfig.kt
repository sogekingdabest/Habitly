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
    /**
     * RAM mínima (bytes, en GB comerciales) por debajo de la cual NO se ofrece el modelo: ni
     * descarga ni carga. El pico de memoria de estos motores ronda 2,3x el tamaño del fichero
     * —la cifra sale de los `estimatedPeakMemoryInBytes` que publica Google en el catálogo del
     * AI Edge Gallery—, y pasarse no da una excepción: mata el proceso.
     */
    val minRamBytes: Long,
    /** RAM a partir de la cual el modelo va holgado. Entre [minRamBytes] y esta, se avisa. */
    val recommendedRamBytes: Long,
    /** Tope de tokens (contexto + generación) del EngineConfig. Ajustable por modelo. */
    val maxTokens: Int = 4096,
    /** Temperatura del turno conversacional (el turno de extracción usa la suya, más baja). */
    val defaultTemperature: Double = 0.9,
    /** El artefacto trae drafter MTP (speculative decoding); solo aporta en GPU. */
    val supportsSpeculativeDecoding: Boolean = true,
    /** El modelo hace function-calling con fiabilidad: usa el extractor por tools en vez del de JSON. */
    val supportsToolCalling: Boolean = true,
    /**
     * El grafo del modelo corre en los delegados de GPU. Cuando es `false` se va DIRECTO a CPU
     * sin intentar GPU: un delegado tragándose un grafo que no soporta puede irse por SIGSEGV,
     * y eso no lo recoge el `catch` del fallback (ver [AiAssistantRepositoryImpl.loadModelLocked]).
     */
    val supportsGpu: Boolean = true
)

private const val GB = 1_073_741_824L

object AvailableAiModels {
    // sizeBytes y sha256 salen del puntero LFS del repo de Hugging Face (ver KDoc de
    // AiModelConfig.sha256): son los valores exactos del artefacto publicado, no estimaciones.
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
     * El modelo para dispositivos modestos (4 GB de RAM, donde los Gemma 4 ni se plantean).
     * LFM2.5 declara español entre sus 8 idiomas y su model card lo recomienda expresamente
     * para "agentic tasks, data extraction and RAG", que es justo lo que hacemos.
     *
     * Dos particularidades frente a los Gemma, ambas de su model card:
     *  - Es CPU ONLY: "the hybrid conv graph is not supported by current GPU delegates".
     *  - Su function-calling usa un formato pythónico propio entre tokens especiales, que no
     *    es el que espera nuestra ruta de `tool(...)`; va por el extractor de JSON.
     *
     * Requiere litert-lm >= 0.14 y tenemos 0.14.0 fijado, así que no toca dependencias.
     */
    val LFM2_5_1_2B_Instruct = AiModelConfig(
        id = "lfm2-5-1-2b",
        name = "LFM2.5 (1.2B) - Ligero",
        downloadUrl = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm",
        sizeBytes = 735_999_360L, // 736 MB
        filename = "lfm2-5-1-2b-int4.litertlm",
        sha256 = "e51f10c7b3b8e0568148484e2ece6473f4195ea5a3111b333fdaf9496434fe05",
        // Umbrales MEDIDOS, no estimados: con la ventana de 4096 la app marca 2,10 GB de RSS
        // (1,04 GB private dirty, irrecuperable; el resto son páginas limpias del fichero que
        // el kernel sí puede desalojar). En un móvil de 4 GB eso entra, pero justo: por eso
        // recomendamos 6 y el de 4 GB recibe el aviso en vez de vía libre.
        minRamBytes = 4 * GB,
        recommendedRamBytes = 6 * GB,
        // 4096 como los demás. Se probó con 2048 para ahorrar RAM y fue un error: el system
        // prompt de la app ya ocupa ~650 tokens y un menú semanal ronda los 1200, así que un
        // solo intercambio agotaba la ventana (y el prefill del segundo mensaje habría
        // empezado a fallar). Doblar la ventana cuesta 240 MB medidos — la KV cache de LFM2
        // NO es tan barata como sugiere su model card—, pero sin ella no hay caso de uso.
        maxTokens = 4096,
        supportsSpeculativeDecoding = false,
        supportsToolCalling = false,
        supportsGpu = false
    )

    // Orden de menor a mayor exigencia: en el desplegable, lo que un móvil modesto puede usar
    // aparece primero en vez de quedar debajo de dos modelos bloqueados.
    val models = listOf(LFM2_5_1_2B_Instruct, Gemma4_E2B_IT, Gemma4_E4B_IT)
}
