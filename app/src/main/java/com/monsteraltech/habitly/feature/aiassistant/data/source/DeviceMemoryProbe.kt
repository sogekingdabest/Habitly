package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAM total del dispositivo, en los GB de la ficha comercial.
 *
 * El matiz importante: `MemoryInfo.totalMem` NO devuelve los GB que anuncia el fabricante,
 * sino los que ve el kernel tras descontar lo que se reservan la GPU, la radio y demás — en un
 * móvil de "4 GB" suele rondar 3,6. Como los umbrales del catálogo están escritos en GB
 * comerciales, comparar contra `totalMem` en crudo dejaría fuera a dispositivos que sí
 * cumplen. En API 34+ existe `advertisedMem`, que devuelve justo la cifra comercial; por
 * debajo se redondea al escalón conocido más cercano. Es el mismo enfoque que usa el AI Edge
 * Gallery de Google en su `MemoryWarning`.
 */
@Singleton
class DeviceMemoryProbe @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** RAM comercial en bytes, o 0 si el sistema no supo responder. */
    fun totalRamBytes(): Long {
        val activityManager = context.getSystemService<ActivityManager>() ?: return 0L
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // advertisedMem ya viene en GB comerciales: no hay que redondear nada.
            if (info.advertisedMem > 0L) return info.advertisedMem
        }
        return roundToCommercialTier(info.totalMem)
    }

    /** RAM libre ahora mismo. Solo informativa: puede cambiar entre la lectura y la carga. */
    fun availableRamBytes(): Long {
        val activityManager = context.getSystemService<ActivityManager>() ?: return 0L
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem
    }

}

private const val GB = 1_073_741_824L

/** Tamaños de RAM que se fabrican de verdad. */
private val COMMERCIAL_TIERS = longArrayOf(
    1 * GB, 2 * GB, 3 * GB, 4 * GB, 6 * GB, 8 * GB, 12 * GB, 16 * GB, 24 * GB, 32 * GB
)

/**
 * Fracción del total anunciado que como mínimo llega a reportar `totalMem`. Lo habitual es
 * 88-93%; con 0.80 damos margen a dispositivos que reservan mucho sin que ninguno suba de
 * escalón por error (0.80 * 6 GB = 4,8 GB, muy por encima de lo que reporta uno de 4 GB).
 */
private const val MIN_REPORTED_FRACTION = 0.80

/**
 * Redondea lo que reporta el kernel al escalón comercial correspondiente. Si queda por debajo
 * del más pequeño (o la lectura falló) se devuelve tal cual: preferimos un valor bajo real a
 * inventarnos memoria que no existe.
 */
internal fun roundToCommercialTier(reportedBytes: Long): Long {
    if (reportedBytes <= 0L) return 0L
    return COMMERCIAL_TIERS.lastOrNull { reportedBytes >= it * MIN_REPORTED_FRACTION }
        ?: reportedBytes
}
