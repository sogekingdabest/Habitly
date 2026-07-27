package com.monsteraltech.habitly.feature.aiassistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La puerta que evita que la app se cierre en seco al cargar un modelo demasiado grande.
 * Se prueba con dispositivos reales del parque de testers, no con números abstractos: el fallo
 * que originó esto fue un POCO M4 5G de 4 GB intentando cargar el Gemma 4 E2B.
 */
class ModelCompatibilityTest {

    private companion object {
        const val GB = 1_073_741_824L
        const val POCO_M4_5G = 4 * GB
    }

    @Test
    fun `un movil de 4 GB no puede con ninguno de los Gemma 4`() {
        assertEquals(
            ModelCompatibility.Unsupported,
            AvailableAiModels.Gemma4_E2B_IT.compatibilityWith(POCO_M4_5G)
        )
        assertEquals(
            ModelCompatibility.Unsupported,
            AvailableAiModels.Gemma4_E4B_IT.compatibilityWith(POCO_M4_5G)
        )
    }

    @Test
    fun `un movil de 4 GB puede con el modelo ligero, avisando`() {
        // Es la razón de ser del LFM2.5 en el catálogo: que estos dispositivos tengan algo.
        // Pero medido en dispositivo son 2,10 GB de RSS, así que en 4 GB entra justo y toca
        // avisar; la vía libre empieza en 6 GB.
        val compatibility = AvailableAiModels.LFM2_5_1_2B_Instruct.compatibilityWith(POCO_M4_5G)
        assertEquals(ModelCompatibility.Tight, compatibility)
        assertTrue("El POCO tiene que poder usarlo", compatibility.canUse)
    }

    @Test
    fun `todo dispositivo con 4 GB o mas tiene al menos un modelo utilizable`() {
        // Por debajo de 4 GB no ofrecemos nada, y es deliberado: el modelo más liviano ya mide
        // 2,10 GB de RSS. Prometer un asistente ahí sería prometer un cierre en seco.
        listOf(4 * GB, 6 * GB, 8 * GB, 12 * GB).forEach { ram ->
            assertTrue(
                "Con $ram bytes de RAM no hay ningún modelo ofrecible",
                AvailableAiModels.models.any { it.compatibilityWith(ram).canUse }
            )
        }
        assertTrue(
            "Con 3 GB no debería ofrecerse nada",
            AvailableAiModels.models.none { it.compatibilityWith(3 * GB).canUse }
        )
    }

    @Test
    fun `entre el minimo y el recomendado se avisa pero se deja continuar`() {
        // 6 GB cumple el mínimo del E2B (6) pero no lo recomendado (8): aviso, no bloqueo.
        val compatibility = AvailableAiModels.Gemma4_E2B_IT.compatibilityWith(6 * GB)
        assertEquals(ModelCompatibility.Tight, compatibility)
        assertTrue(compatibility.canUse)
    }

    @Test
    fun `una lectura de RAM fallida avisa en vez de bloquear o dar via libre`() {
        // Si no sabemos la memoria, ni cerramos la puerta a un dispositivo capaz ni dejamos
        // pasar en silencio a uno que no lo es.
        val compatibility = AvailableAiModels.Gemma4_E4B_IT.compatibilityWith(0L)
        assertEquals(ModelCompatibility.Tight, compatibility)
    }

    @Test
    fun `el catalogo declara umbrales coherentes`() {
        AvailableAiModels.models.forEach { model ->
            assertTrue(
                "${model.id} no declara RAM mínima",
                model.minRamBytes > 0L
            )
            assertTrue(
                "${model.id} recomienda menos RAM de la que exige",
                model.recommendedRamBytes >= model.minRamBytes
            )
            // El pico de memoria ronda 2,3x el fichero (cifra de los estimatedPeakMemory que
            // publica Google). Es un SUELO, no una promesa: el LFM2.5 medido en dispositivo
            // sale a 2,86x porque su KV cache pesa más de lo que sugiere su model card. Si el
            // mínimo no cubre ni el suelo, el umbral está mal y vuelven los cierres en seco.
            assertTrue(
                "${model.id} declara una RAM mínima por debajo de su pico estimado",
                model.minRamBytes >= (model.sizeBytes * 23) / 10
            )
        }
    }

    @Test
    fun `el modelo ligero va a CPU y sin function-calling`() {
        // Su model card: el grafo de conv híbrida no lo soportan los delegados de GPU, y su
        // function-calling usa un formato pythónico propio que nuestra ruta de tools no habla.
        val light = AvailableAiModels.LFM2_5_1_2B_Instruct
        assertFalse("LFM2.5 no corre en GPU", light.supportsGpu)
        assertFalse("LFM2.5 no usa nuestra ruta de tools", light.supportsToolCalling)
        assertFalse("Sin GPU, MTP no aporta", light.supportsSpeculativeDecoding)
    }
}
