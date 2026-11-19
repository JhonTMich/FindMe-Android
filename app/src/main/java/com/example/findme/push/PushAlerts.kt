package com.example.findme.push

import com.example.findme.data.local.AccountAlert
import com.example.findme.data.local.AccountSettings
import com.example.findme.util.NotificationHelper

/** What one push (FCM data message sent by the send-push function) shows, before the switches are checked. */
/** [person] is the connected person the alert is about: the sender of a message, or whose status or activity changed. */
data class PushAlert(val alert: AccountAlert, val channel: NotificationHelper.Channel, val id: Int, val title: String, val text: String,
    val person: String)

object PushAlerts {
    const val STATUS_ENDED = "Vuelve a mostrar su horario."

    fun statusText(location: String, message: String?) =
        listOfNotNull(location.trim(), message?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

    fun activityText(title: String, location: String?) =
        "Actividad en curso: " + listOfNotNull(title.trim(), location?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

    /** Null for unknown or incomplete messages. */
    fun from(data: Map<String, String>): PushAlert? {
        val name = data["name"]?.takeIf { it.isNotBlank() } ?: "Tu gente"
        val person = data["user_id"]
        return when (data["type"]) {
            "message" -> {
                val id = data["id"] ?: return null
                val text = data["message"]?.takeIf { it.isNotBlank() } ?: return null
                // The database names the author of a message sender_id, not user_id.
                val sender = data["sender_id"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Messages, NotificationHelper.Channel.Messages, NotificationHelper.messageId(id), name, text, sender)
            }
            "status" -> {
                val location = data["location"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Statuses, NotificationHelper.Channel.Statuses, NotificationHelper.personId(person ?: return null),
                    name, statusText(location, data["message"]), person)
            }
            "status_end" -> PushAlert(AccountAlert.Statuses, NotificationHelper.Channel.Statuses,
                NotificationHelper.personId(person ?: return null), name, STATUS_ENDED, person)
            "activity" -> {
                val title = data["title"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Activities, NotificationHelper.Channel.Activities, NotificationHelper.personId(person ?: return null),
                    name, activityText(title, data["location"]), person)
            }
            else -> null
        }
    }

    fun allowed(alert: PushAlert, settings: AccountSettings) = when (alert.alert) {
        AccountAlert.Activities -> settings.activities
        AccountAlert.Statuses -> settings.statuses
        AccountAlert.Messages -> settings.messages
    }
}
