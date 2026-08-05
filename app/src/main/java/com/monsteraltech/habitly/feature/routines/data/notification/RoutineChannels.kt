package com.monsteraltech.habitly.feature.routines.data.notification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper

/**
 * The reminder notification channels, one per [NotificationLevel].
 *
 * Android freezes a channel's sound, vibration and importance the moment it is created — only its
 * name and description can change afterwards. So instead of building a sound picker that could never
 * apply its choice, the app ships three fixed channels and sends the user to the system settings of
 * whichever one they want to tune (see `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` in the
 * settings screen). Each routine then just points at a level.
 */
object RoutineChannels {

    private const val GROUP_ID = "routines"

    /** The single channel used before levels existed. Retired in [ensureChannels]. */
    private const val LEGACY_CHANNEL_ID = "routines_reminders"

    const val CHANNEL_SILENT = "routines_silent"
    const val CHANNEL_DEFAULT = "routines_default"
    const val CHANNEL_HIGH = "routines_high"

    fun channelIdFor(level: NotificationLevel): String = when (level) {
        NotificationLevel.SILENT -> CHANNEL_SILENT
        NotificationLevel.DEFAULT -> CHANNEL_DEFAULT
        NotificationLevel.HIGH -> CHANNEL_HIGH
    }

    /** Every channel this app owns, in the order the settings screen lists them. */
    val allChannelIds: List<String> = listOf(CHANNEL_SILENT, CHANNEL_DEFAULT, CHANNEL_HIGH)

    /**
     * Creates the three channels (idempotent — re-creating an existing channel does not reset what
     * the user configured) and retires the legacy single channel.
     *
     * Called on app start rather than when the first reminder fires, because the settings screen
     * links straight into these channels and they have to exist by then.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Texts follow the language chosen in the app, not the system one.
        val ctx = LocaleHelper.wrap(context)

        manager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, ctx.getString(R.string.routines_channel_group))
        )

        listOf(
            Triple(CHANNEL_SILENT, NotificationManager.IMPORTANCE_LOW, R.string.routines_channel_silent),
            Triple(CHANNEL_DEFAULT, NotificationManager.IMPORTANCE_DEFAULT, R.string.routines_channel_default),
            Triple(CHANNEL_HIGH, NotificationManager.IMPORTANCE_HIGH, R.string.routines_channel_high)
        ).forEach { (id, importance, nameRes) ->
            val channel = NotificationChannel(id, ctx.getString(nameRes), importance).apply {
                description = ctx.getString(R.string.routines_channel_desc)
                group = GROUP_ID
                if (id == CHANNEL_SILENT) {
                    setSound(null, null)
                    enableVibration(false)
                }
            }
            manager.createNotificationChannel(channel)
        }

        // The old channel would otherwise sit in the settings list forever, doing nothing. Deleting
        // it bumps the "deleted channels" counter Android shows as an anti-spam measure; that is a
        // one-off cost worth paying to leave a clean list.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }
}
