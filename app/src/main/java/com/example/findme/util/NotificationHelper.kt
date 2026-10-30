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
    private const val MESSAGES_CHANNEL = "findme_messages"

    private const val CHANNEL_ID = "findme_status_channel"
    private const val CHANNEL_NAME = "Actualizaciones de Estado"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notificaciones de cambios de estado del usuario"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
            notificationManager?.createNotificationChannel(NotificationChannel(MESSAGES_CHANNEL, "Mensajes de tu gente", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun showMessageNotification(context: Context, id: String, sender: String, message: String) {
        val intent = Intent(context, com.example.findme.MainActivity::class.java).apply { action = OPEN_PEOPLE }
        val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(sender).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(pending)
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(id.hashCode(), notification) }
        catch (_: SecurityException) { }
    }

    fun showStatusNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: Exception) {}
    }
}
