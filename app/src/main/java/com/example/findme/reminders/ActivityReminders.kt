package com.example.findme.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.formatScheduleTime
import com.example.findme.domain.model.nextOccurrence
import com.example.findme.util.NotificationHelper
import com.example.findme.widget.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * "Prácticas empieza en 10 min": one alarm for the next of your own activities. When it fires it shows
 * the reminder and schedules the following one. Your schedule and the switch are read from the widget
 * cache and the account settings, so it works with Near closed and without network.
 */
object ActivityReminders {
    data class Next(val schedule: ScheduleDto, val start: ZonedDateTime, val remindAt: ZonedDateTime)

    /** The earliest reminder still ahead of [now]: each activity [minutes] before its next weekly start. */
    fun next(schedules: List<ScheduleDto>, zone: ZoneId, now: ZonedDateTime, minutes: Int): Next? {
        val earliestStart = now.withZoneSameInstant(zone).plusMinutes(minutes.toLong())
        return schedules.mapNotNull { schedule ->
            schedule.nextOccurrence(earliestStart)?.let { occurrence ->
                val start = occurrence.window.start
                Next(occurrence.schedule, start, start.minusMinutes(minutes.toLong()))
            }
        }.minByOrNull { it.remindAt.toInstant() }
    }

    fun text(next: Next, minutes: Int): Pair<String, String> {
        val lead = if (minutes >= 60) "${minutes / 60} h" else "$minutes min"
        val title = "${next.schedule.title.trim()} empieza en $lead"
        val body = listOf("A las ${formatScheduleTime(next.schedule.startTime)}", next.schedule.location.trim())
            .filter { it.isNotEmpty() }.joinToString(" · ")
        return title to body
    }

    private fun intent(context: Context) = PendingIntent.getBroadcast(context, 7101,
        Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(intent(context))
    }

    /** True when Android lets reminders arrive at the exact minute (Android 12+ asks the person). */
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true

    /** Recomputes the single pending alarm from the current schedule and settings. */
    suspend fun reschedule(context: Context) {
        cancel(context)
        val data = WidgetStore.data(context) ?: return
        val settings = AccountSettingsRepository(context).settings(data.owner).first()
        if (!settings.reminders) return
        val zone = data.profiles.find { it.id == data.owner }?.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val own = data.schedules.filter { it.userId == data.owner }
        val next = next(own, zone, ZonedDateTime.now(zone), settings.reminderMinutes) ?: return
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = next.remindAt.toInstant().toEpochMilli()
        if (exactAllowed(context)) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
                return
            } catch (_: SecurityException) {
                // Android may revoke exact alarm access between the check and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
    }

    /** Fired by the alarm: shows the reminder that is due now, if it still is, then schedules the next. */
    suspend fun fire(context: Context) {
        val data = WidgetStore.data(context)
        if (data != null) {
            val settings = AccountSettingsRepository(context).settings(data.owner).first()
            val zone = data.profiles.find { it.id == data.owner }?.timezone
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            val minutes = settings.reminderMinutes
            // Inexact alarms may run a little late: accept a reminder that became due in the last 15 minutes.
            val due = next(data.schedules.filter { it.userId == data.owner }, zone,
                ZonedDateTime.now(zone).minusMinutes(15), minutes)
            if (settings.reminders && due != null && !due.remindAt.toInstant().isAfter(Instant.now().plusSeconds(60))
                && due.start.toInstant().isAfter(Instant.now())) {
                val (title, body) = text(due, minutes)
                NotificationHelper.show(context, NotificationHelper.Channel.Reminders,
                    "reminder:${due.schedule.id}".hashCode(), title, body)
            }
        }
        reschedule(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ActivityReminders.fire(context.applicationContext) } finally { pending.finish() }
        }
    }
}

/** Alarms are lost on reboot and must follow time or time-zone changes. */
class RemindersRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ActivityReminders.reschedule(context.applicationContext) } finally { pending.finish() }
        }
    }
}
