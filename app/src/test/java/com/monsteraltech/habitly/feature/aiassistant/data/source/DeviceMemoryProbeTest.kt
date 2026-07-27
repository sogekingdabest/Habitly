package com.monsteraltech.habitly.feature.aiassistant.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El redondeo al escalón comercial. Sin él comparamos peras con manzanas: los umbrales del
 * catálogo están en los GB de la caja del móvil y `totalMem` reporta siempre menos, así que un
 * dispositivo que cumple justo quedaría fuera por unos cientos de MB que se queda el kernel.
 */
class DeviceMemoryProbeTest {

    private companion object {
        const val GB = 1_073_741_824L
    }

    @Test
    fun `lo que reporta el kernel se redondea a la RAM anunciada`() {
        // Valores medidos típicos: el kernel reporta un 88-93% de lo anunciado.
        assertEquals(4 * GB, roundToCommercialTier(3_700_000_000L))  // POCO M4 5G "4 GB"
        assertEquals(6 * GB, roundToCommercialTier(5_600_000_000L))
        assertEquals(8 * GB, roundToCommercialTier(7_400_000_000L))
        assertEquals(12 * GB, roundToCommercialTier(11_200_000_000L))
    }

    @Test
    fun `un movil de 4 GB nunca se cuela como si fuera de 6`() {
        // Es el error que haría volver el cierre en seco: creerle 6 GB a quien tiene 4 le
        // desbloquearía el Gemma 4 E2B.
        val reportedByA4GbDevice = 3_900_000_000L
        assertEquals(4 * GB, roundToCommercialTier(reportedByA4GbDevice))
    }

    @Test
    fun `una lectura exacta se queda en su escalon`() {
        assertEquals(4 * GB, roundToCommercialTier(4 * GB))
        assertEquals(8 * GB, roundToCommercialTier(8 * GB))
    }

    @Test
    fun `una lectura invalida no inventa memoria`() {
        assertEquals(0L, roundToCommercialTier(0L))
        assertEquals(0L, roundToCommercialTier(-1L))
    }

    @Test
    fun `una lectura por debajo del escalon mas pequeno se devuelve tal cual`() {
        // Preferimos un valor bajo real (que bloqueará todo) a redondear hacia arriba.
        val tiny = 500_000_000L
        assertEquals(tiny, roundToCommercialTier(tiny))
    }
}
