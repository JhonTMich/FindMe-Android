package com.example.findme.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.findme.R

object NotificationHelper {
    const val OPEN_PEOPLE = "com.example.findme.OPEN_PEOPLE"
    const val OPEN_SCHEDULE = "com.example.findme.OPEN_SCHEDULE"

    /** One Android channel per kind, so each can also be silenced from the system settings. */
    enum class Channel(val id: String, val title: String, val description: String) {
        Activities("near_activities", "Actividades de tu gente", "Cuando una persona conectada comienza una actividad."),
        Statuses("near_statuses", "Estados de tu gente", "Cuando tu gente comparte, cambia o quita su estado."),
        Messages("near_messages", "Mensajes de tu gente", "Mensajes rápidos que recibes."),
        Reminders("near_reminders", "Recordatorios de tus actividades", "Un aviso antes de que empiece cada actividad de tu horario.")
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        Channel.entries.forEach { channel ->
            manager.createNotificationChannel(NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_HIGH)
                .apply { description = channel.description })
        }
    }

    private fun open(context: Context, action: String): PendingIntent {
        val intent = Intent(context, com.example.findme.MainActivity::class.java).apply { this.action = action }
        return PendingIntent.getActivity(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Status and activity changes of one person replace each other instead of piling up. */
    fun personId(personId: String) = "person:$personId".hashCode()
    fun messageId(messageId: String) = "message:$messageId".hashCode()

    fun show(context: Context, channel: Channel, id: Int, title: String, text: String) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, if (channel == Channel.Reminders) OPEN_SCHEDULE else OPEN_PEOPLE))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == Channel.Messages) NotificationCompat.CATEGORY_MESSAGE else if (channel == Channel.Reminders) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .build()
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(id, notification) }
        catch (_: SecurityException) { }
    }

    fun showMessageNotification(context: Context, id: String, sender: String, message: String) =
        show(context, Channel.Messages, messageId(id), sender, message)

    fun showStatusNotification(context: Context, personId: String, title: String, message: String, activity: Boolean = false) =
        show(context, if (activity) Channel.Activities else Channel.Statuses, personId(personId), title, message)
}
