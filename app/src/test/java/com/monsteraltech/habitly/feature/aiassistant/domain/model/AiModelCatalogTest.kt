package com.monsteraltech.habitly.feature.aiassistant.domain.model

import org.junit.Assert.assertEquals
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
