package com.monsteraltech.habitly.feature.routines.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.app.NotificationCompat
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.NotificationLevel
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper

/**
 * Builds and posts a routine reminder. Shared by the periodic reminder worker and by the snooze
 * path, so the notification looks and behaves the same however it was triggered.
 */
object RoutineNotifier {

    /** Side of the generated large icon, in pixels. Comfortably above any launcher density. */
    private const val LARGE_ICON_PX = 192

    /**
     * Posts the reminder for a routine.
     *
     * [icon] is an emoji or empty. It goes both in the title text — free, and survives anything —
     * and as the large icon, so the routine is recognisable at a glance without reading.
     */
    fun show(
        context: Context,
        routineId: String,
        title: String,
        icon: String,
        level: NotificationLevel,
        type: RoutineType,
        userId: String,
        householdId: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Context carrying the language chosen in Settings: applicationContext does not reflect it.
        val ctx = LocaleHelper.wrap(context)

        val openIntent = Intent().apply {
            setClassName(context, "com.monsteraltech.habitly.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("routine_id", routineId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            routineId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (icon.isBlank()) title else "$icon $title"

        val builder = NotificationCompat.Builder(context, RoutineChannels.channelIdFor(level))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(ctx.getString(R.string.routines_notification_title))
            .setContentText(ctx.getString(R.string.routines_notification_text, displayTitle))
            .setPriority(level.toCompatPriority())
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                ctx.getString(R.string.routines_notification_snooze),
                RoutineActionReceiver.pendingIntent(
                    context, RoutineActionReceiver.ACTION_SNOOZE,
                    routineId, title, icon, level, type, userId, householdId
                )
            )
            .addAction(
                0,
                ctx.getString(R.string.routines_notification_done),
                RoutineActionReceiver.pendingIntent(
                    context, RoutineActionReceiver.ACTION_DONE,
                    routineId, title, icon, level, type, userId, householdId
                )
            )

        emojiBitmap(icon)?.let { builder.setLargeIcon(it) }

        manager.notify(routineId.hashCode(), builder.build())
    }

    /** Removes the reminder currently on screen for [routineId], if any. */
    fun cancel(context: Context, routineId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(routineId.hashCode())
    }

    /**
     * Draws the emoji onto a transparent square so it can be used as the notification's large icon.
     * Cheap enough to do inline and it needs no image library. Returns null when there is no icon.
     */
    private fun emojiBitmap(icon: String): Bitmap? {
        if (icon.isBlank()) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(LARGE_ICON_PX, LARGE_ICON_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = LARGE_ICON_PX * 0.72f
                textAlign = Paint.Align.CENTER
                color = Color.BLACK
            }
            // Vertically centre using the font metrics rather than the glyph bounds, which vary a
            // lot between emoji and would make each one sit at a different height.
            val metrics = paint.fontMetrics
            val baseline = LARGE_ICON_PX / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(icon, LARGE_ICON_PX / 2f, baseline, paint)
            bitmap
        }.getOrNull()
    }

    private fun NotificationLevel.toCompatPriority(): Int = when (this) {
        NotificationLevel.SILENT -> NotificationCompat.PRIORITY_LOW
        NotificationLevel.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotificationLevel.HIGH -> NotificationCompat.PRIORITY_HIGH
    }
}
