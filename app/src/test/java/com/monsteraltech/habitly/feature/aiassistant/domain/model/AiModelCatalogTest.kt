package com.monsteraltech.habitly.feature.aiassistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blindaje del catálogo de modelos. Descargamos gigabytes de un servidor de terceros y se los
 * pasamos a un motor de inferencia nativo, así que la única defensa real es el SHA-256 fijado.
 * Estos tests existen para que añadir un modelo sin verificar rompa la build en vez de degradar
 * la seguridad en silencio.
 */
class AiModelCatalogTest {

    private val hexSha256 = Regex("^[0-9a-f]{64}$")

    @Test
    fun `todos los modelos del catalogo llevan un SHA-256 valido`() {
        AvailableAiModels.models.forEach { model ->
            assertTrue(
                "El modelo ${model.id} no tiene un SHA-256 hexadecimal de 64 caracteres: '${model.sha256}'",
                hexSha256.matches(model.sha256)
            )
        }
    }

    @Test
    fun `todos los modelos se descargan por HTTPS`() {
        AvailableAiModels.models.forEach { model ->
            assertTrue(
                "El modelo ${model.id} no se descarga por HTTPS: ${model.downloadUrl}",
                model.downloadUrl.startsWith("https://")
            )
        }
    }

    @Test
    fun `los identificadores y nombres de fichero del catalogo son unicos`() {
        // Dos modelos con el mismo filename se pisarían en disco y cada uno fallaría la
        // verificación de integridad del otro.
        val ids = AvailableAiModels.models.map { it.id }
        val filenames = AvailableAiModels.models.map { it.filename }
        assertEquals("Hay ids duplicados en el catálogo", ids.size, ids.distinct().size)
        assertEquals("Hay filenames duplicados en el catálogo", filenames.size, filenames.distinct().size)
    }

    @Test
    fun `una penalizacion de repeticion por debajo de 1 reventaria en el primer mensaje`() {
        // RepetitionPenaltyConfig exige >= 1.0f en su init: un 0,9 en el catálogo no se vería al
        // compilar, se vería como IllegalArgumentException al enviar el primer mensaje, en el
        // móvil del usuario. Y 1.0 exacto es "sin penalización", así que declararlo es ruido.
        AvailableAiModels.models.forEach { model ->
            val penalty = model.repetitionPenalty ?: return@forEach
            assertTrue(
                "El modelo ${model.id} declara una penalización inválida: $penalty",
                penalty > 1.0f
            )
        }
    }

    @Test
    fun `LFM3 declara lo que dice su manifiesto`() {
        // Cada uno de estos campos sale del litertlm_manifest.json del fichero que descargamos,
        // no de la model card de Liquid: lo que manda es la variante convertida.
        val lfm3 = AvailableAiModels.LFM3_1_7B

        // backends [cpu, gpu], default_backend gpu — al contrario que el LFM2.5.
        assertTrue("LFM3 sí corre en GPU", lfm3.supportsGpu)
        // El fichero no trae sección de drafter, así que no hay MTP que activar.
        assertFalse("LFM3 no trae drafter", lfm3.supportsSpeculativeDecoding)
        // Sus tool calls vienen en XML propio, que LiteRT-LM no parsea: va por el extractor JSON.
        assertFalse("LFM3 no habla nuestra ruta de tools", lfm3.supportsToolCalling)
        // "Thinks by default (enable_thinking false turns it off)".
        assertTrue("Hay que apagarle el pensar en cada turno", lfm3.thinksByDefault)
        // El fichero declara su propio sampler a temperatura 0,1; es la cifra que acompaña al 1.1.
        assertNotNull("LFM3 declara penalización de repetición", lfm3.repetitionPenalty)
        assertEquals(1.1f, lfm3.repetitionPenalty!!, 0.0f)
        assertEquals(0.1, lfm3.defaultTemperature, 0.0)
        // Descargamos la variante ekv4096: pedir más ventana de la que el fichero trae no es una
        // opción, y pedir menos fue el error que ya cometimos con el LFM2.5.
        assertEquals(4096, lfm3.maxTokens)
        assertTrue(
            "LFM3 tiene que ser la variante ekv4096",
            lfm3.downloadUrl.endsWith("LFM3-1.7B_mixed_int4_ekv4096.litertlm")
        )
    }

    @Test
    fun `el tamano declarado es coherente con el umbral de validacion`() {
        // isValidModelFile acepta a partir del 95% de sizeBytes. Un tamaño a cero o negativo
        // haría que cualquier fichero (incluida una página de error) pasara por válido.
        AvailableAiModels.models.forEach { model ->
            assertTrue(
                "El modelo ${model.id} declara un tamaño no plausible: ${model.sizeBytes}",
                model.sizeBytes > 100_000_000L
            )
        }
    }
}
