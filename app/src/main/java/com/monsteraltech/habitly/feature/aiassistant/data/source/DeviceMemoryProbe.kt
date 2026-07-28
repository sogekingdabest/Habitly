package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Total device RAM, in the GB figure printed on the box.
 *
 * The catch: `MemoryInfo.totalMem` does **not** report the manufacturer's number but what the
 * kernel sees once the GPU, the radio and the rest have taken their reservations — a "4 GB" phone
 * usually reads about 3.6. Since the catalog thresholds are written in commercial GB, comparing
 * against raw `totalMem` would rule out devices that do in fact qualify. API 34+ has
 * `advertisedMem`, which returns exactly the commercial figure; below that the reading is rounded
 * to the nearest known tier. Same approach Google's AI Edge Gallery takes in its `MemoryWarning`.
 */
@Singleton
class DeviceMemoryProbe @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Commercial RAM in bytes, or 0 if the system had no answer. */
    fun totalRamBytes(): Long {
        val activityManager = context.getSystemService<ActivityManager>() ?: return 0L
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // advertisedMem already comes in commercial GB: nothing to round.
            if (info.advertisedMem > 0L) return info.advertisedMem
        }
        return roundToCommercialTier(info.totalMem)
    }

    /** Free RAM right now. Informational only: it can change between the reading and the load. */
    fun availableRamBytes(): Long {
        val activityManager = context.getSystemService<ActivityManager>() ?: return 0L
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem
    }

}

private const val GB = 1_073_741_824L

/** RAM sizes that are actually manufactured. */
private val COMMERCIAL_TIERS = longArrayOf(
    1 * GB, 2 * GB, 3 * GB, 4 * GB, 6 * GB, 8 * GB, 12 * GB, 16 * GB, 24 * GB, 32 * GB
)

/**
 * Smallest fraction of the advertised total that `totalMem` still reports. The usual range is
 * 88-93%; 0.80 leaves room for devices that reserve a lot without letting any of them jump a tier
 * by mistake (0.80 * 6 GB = 4.8 GB, well above anything a 4 GB device reports).
 */
private const val MIN_REPORTED_FRACTION = 0.80

/**
 * Rounds the kernel's reading up to its commercial tier. Below the smallest tier — or on a failed
 * reading — the value passes through untouched: a real low number beats inventing memory that is
 * not there.
 */
internal fun roundToCommercialTier(reportedBytes: Long): Long {
    if (reportedBytes <= 0L) return 0L
    return COMMERCIAL_TIERS.lastOrNull { reportedBytes >= it * MIN_REPORTED_FRACTION }
        ?: reportedBytes
}
