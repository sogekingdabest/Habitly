package com.monsteraltech.habitly.feature.routines.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper
import com.monsteraltech.habitly.feature.settings.data.SettingsRepositoryImpl
import com.monsteraltech.habitly.feature.routines.domain.util.RoutineSchedule
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

/**
 * Notifica una rutina a su hora. El trabajo es periódico diario, así que en cada disparo
 * hay que decidir si hoy toca de verdad.
 *
 * Relee la rutina de Firestore (que resuelve desde su caché offline, sin necesitar red) en vez
 * de fiarse de lo que se guardó al programar el recordatorio: la frecuencia, la pausa o la
 * última vez que se hizo pueden haber cambiado desde entonces, y en las rutinas "cada N días"
 * el "¿toca hoy?" depende justo de ese último dato.
 */
class RoutineReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Interruptor maestro de recordatorios (Ajustes). Si está apagado, no molestamos.
        if (!SettingsRepositoryImpl.readRemindersEnabled(applicationContext)) return Result.success()

        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.failure()
        val fallbackTitle = inputData.getString(KEY_ROUTINE_TITLE) ?: return Result.failure()
        val userId = inputData.getString(KEY_USER_ID).orEmpty()
        val householdId = inputData.getString(KEY_HOUSEHOLD_ID).orEmpty()
        val type = runCatching {
            RoutineType.valueOf(inputData.getString(KEY_ROUTINE_TYPE) ?: RoutineType.PERSONAL.name)
        }.getOrDefault(RoutineType.PERSONAL)

        val repository = EntryPointAccessors
            .fromApplication(applicationContext, RoutinesEntryPoint::class.java)
            .routinesRepository()

        val routineResult = repository.getRoutine(userId, householdId, routineId, type)

        // No se pudo leer (sin red y sin caché): avisamos con lo último que sabíamos.
        if (routineResult.isFailure) {
            showNotification(fallbackTitle, routineId)
            return Result.success()
        }

        // Se leyó bien pero no existe: la rutina se borró, el recordatorio sobra.
        val routine = routineResult.getOrNull() ?: return Result.success()

        // No molestamos si hoy no toca, si está en pausa o si ya está hecha.
        if (!RoutineSchedule.isPendingOn(routine, LocalDate.now())) return Result.success()

        // Si la rutina está asignada a otro miembro, no es asunto de este usuario.
        // Esto da el "te toca a ti" sin necesitar FCM ni backend.
        val assignedTo = routine.assignedTo
        if (assignedTo != null && userId.isNotBlank() && assignedTo != userId) {
            return Result.success()
        }

        showNotification(routine.title, routineId)
        return Result.success()
    }

    private fun showNotification(title: String, routineId: String) {
        val channelId = "routines_reminders"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Contexto con el idioma elegido en Ajustes: el applicationContext no lo refleja (solo
        // el de la Activity), así que sin esto los textos salían en el idioma del sistema.
        val ctx = LocaleHelper.wrap(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                ctx.getString(R.string.routines_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = ctx.getString(R.string.routines_notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent().apply {
            setClassName(applicationContext, "com.monsteraltech.habitly.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("routine_id", routineId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            routineId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = ctx.getString(R.string.routines_notification_text, title)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(ctx.getString(R.string.routines_notification_title))
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(routineId.hashCode(), notification)
    }

    companion object {
        const val KEY_ROUTINE_TITLE = "routine_title"
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_ROUTINE_TYPE = "routine_type"
        const val KEY_USER_ID = "user_id"
        const val KEY_HOUSEHOLD_ID = "household_id"

        fun getUniqueWorkId(routineId: String): String = "routine_reminder_$routineId"
    }
}
