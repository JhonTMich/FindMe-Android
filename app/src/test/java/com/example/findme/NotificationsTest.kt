package com.example.findme

import com.example.findme.data.local.AccountAlert
import com.example.findme.data.local.AccountSettings
import com.example.findme.data.model.ScheduleDto
import com.example.findme.push.PushAlerts
import com.example.findme.reminders.ActivityReminders
import com.example.findme.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationsTest {
    private val lima = ZoneId.of("America/Lima")
    // Tuesday 17 Nov 2026, 08:00 in Lima.
    private val now = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, lima)
    private fun schedule(id: String, day: Int, start: String, title: String = id, location: String = "") =
        ScheduleDto(id = id, userId = "me", dayOfWeek = day, startTime = start, endTime = "23:00:00", title = title, location = location)

    @Test fun eachPushBecomesTheRightAlert() {
        val message = PushAlerts.from(mapOf("type" to "message", "id" to "m1", "sender_id" to "u1", "name" to "Ana", "message" to "¿Todo bien?"))!!
        assertEquals(AccountAlert.Messages, message.alert)
        assertEquals(NotificationHelper.Channel.Messages, message.channel)
        assertEquals("Ana" to "¿Todo bien?", message.title to message.text)

        val status = PushAlerts.from(mapOf("type" to "status", "user_id" to "u1", "name" to "Ana", "location" to "Biblioteca", "message" to "Estudiando"))!!
        assertEquals("Biblioteca · Estudiando", status.text)
        val ended = PushAlerts.from(mapOf("type" to "status_end", "user_id" to "u1", "name" to "Ana"))!!
        assertEquals(PushAlerts.STATUS_ENDED, ended.text)
        assertEquals("Changes of one person replace each other", status.id, ended.id)

        val activity = PushAlerts.from(mapOf("type" to "activity", "user_id" to "u1", "name" to "Ana", "title" to "Prácticas", "location" to "Lab 3"))!!
        assertEquals(AccountAlert.Activities, activity.alert)
        assertEquals("Actividad en curso: Prácticas · Lab 3", activity.text)
        assertEquals(status.id, activity.id)
    }

    @Test fun eachPushNamesThePersonItIsAbout() {
        // Same keys the database sends (supabase/migrations/20261117210000_notificaciones_push.sql).
        val message = PushAlerts.from(mapOf("type" to "message", "id" to "m1", "sender_id" to "sender", "message" to "Hola",
            "recipient_id" to "me"))!!
        assertEquals("sender", message.person)
        assertEquals("u1", PushAlerts.from(mapOf("type" to "status", "user_id" to "u1", "location" to "Casa"))!!.person)
        assertEquals("u1", PushAlerts.from(mapOf("type" to "status_end", "user_id" to "u1"))!!.person)
        assertEquals("u1", PushAlerts.from(mapOf("type" to "activity", "user_id" to "u1", "title" to "Clase"))!!.person)
        // A message without its author cannot be checked against the people you are connected to.
        assertNull(PushAlerts.from(mapOf("type" to "message", "id" to "m1", "message" to "Hola", "user_id" to "u1")))
    }

    @Test fun incompleteOrUnknownPushesShowNothing() {
        assertNull(PushAlerts.from(mapOf("type" to "message", "id" to "m1")))
        assertNull(PushAlerts.from(mapOf("type" to "status", "name" to "Ana", "location" to "Casa")))
        assertNull(PushAlerts.from(mapOf("type" to "anything", "user_id" to "u1")))
        assertEquals("Tu gente", PushAlerts.from(mapOf("type" to "status_end", "user_id" to "u1"))!!.title)
    }

    @Test fun switchesDecideWhichPushesShow() {
        val message = PushAlerts.from(mapOf("type" to "message", "id" to "m1", "sender_id" to "u1", "message" to "Hola"))!!
        assertTrue(PushAlerts.allowed(message, AccountSettings()))
        assertFalse(PushAlerts.allowed(message, AccountSettings(messages = false)))
        val activity = PushAlerts.from(mapOf("type" to "activity", "user_id" to "u1", "title" to "Clase"))!!
        assertFalse(PushAlerts.allowed(activity, AccountSettings(activities = false)))
        assertTrue(PushAlerts.allowed(activity, AccountSettings(statuses = false, messages = false)))
    }

    @Test fun reminderIsForTheNextActivityMinusTheChosenTime() {
        val schedules = listOf(schedule("later", 2, "18:00:00"), schedule("soon", 2, "09:00:00"), schedule("tomorrow", 3, "07:00:00"))
        val next = ActivityReminders.next(schedules, lima, now, 10)!!
        assertEquals("soon", next.schedule.id)
        assertEquals(now.withHour(8).withMinute(50), next.remindAt)
        // An hour before 09:00 is already past: the next one is the 18:00 activity.
        assertEquals("later", ActivityReminders.next(schedules, lima, now, 60)!!.schedule.id)
    }

    @Test fun reminderWrapsToNextWeekAndUsesTheAccountTimeZone() {
        // Monday's activity already happened this week: next Monday.
        val monday = ActivityReminders.next(listOf(schedule("mon", 1, "09:00:00")), lima, now, 30)!!
        assertEquals(ZonedDateTime.of(2026, 11, 23, 8, 30, 0, 0, lima), monday.remindAt)
        // Same activity today, but asked from another zone: still 09:00 Lima time.
        val fromMadrid = ActivityReminders.next(listOf(schedule("soon", 2, "09:00:00")), lima, now.withZoneSameInstant(ZoneId.of("Europe/Madrid")), 10)!!
        assertEquals(now.withHour(8).withMinute(50).toInstant(), fromMadrid.remindAt.toInstant())
        assertNull(ActivityReminders.next(emptyList(), lima, now, 10))
        assertNull(ActivityReminders.next(listOf(schedule("bad", 2, "not a time")), lima, now, 10))
    }

    @Test fun reminderTextSaysWhatAndWhen() {
        val next = ActivityReminders.next(listOf(schedule("soon", 2, "09:00:00", "Prácticas", "Lab 3")), lima, now, 30)
        assertNotNull(next)
        val (title, body) = ActivityReminders.text(next!!, 30)
        assertEquals("Prácticas empieza en 30 min", title)
        assertTrue(body.startsWith("A las 9:00") && body.endsWith("Lab 3"))
        assertEquals("Prácticas empieza en 1 h", ActivityReminders.text(next, 60).first)
    }
}
