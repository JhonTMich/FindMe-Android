package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.widget.widgetStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class WidgetStatusTest {
    private val now = ZonedDateTime.parse("2026-11-09T10:00:00-05:00[America/Lima]")
    private val lesson = ScheduleDto("class", "me", 1, "09:00", "11:00", "En clase", "Aula 2")

    @Test fun currentActivityAndNextActivityAreSeparate() {
        val next = lesson.copy(id = "next", startTime = "12:00", endTime = "13:00", title = "Almuerzo")
        val status = widgetStatus("Yo", listOf(next, lesson), emptyList(), now)
        assertEquals("En clase", status.title)
        assertTrue(status.detail.contains("Aula 2"))
        assertTrue(status.next.startsWith("Almuerzo"))
    }

    @Test fun cachedScheduleChangesAtEndWithoutFetchingAgain() {
        assertEquals("activity", widgetStatus("Yo", listOf(lesson), emptyList(), now).kind)
        assertEquals("Libre", widgetStatus("Yo", listOf(lesson), emptyList(), now.withHour(11)).title)
    }

    @Test fun manualStatusWinsUntilItsExactExpiry() {
        val manual = StatusOverrideDto("override", "me", true, "En casa", expiresAt = now.plusMinutes(5).toInstant().toString())
        assertEquals("En casa", widgetStatus("Yo", listOf(lesson), listOf(manual), now).title)
        assertEquals("En clase", widgetStatus("Yo", listOf(lesson), listOf(manual), now.plusMinutes(5)).title)
    }

    @Test fun overnightShiftShowsTomorrowOnlyBeforeMidnight() {
        val shift = lesson.copy(dayOfWeek = 7, startTime = "19:00", endTime = "07:00", title = "Turno", category = "work", endsNextDay = true)
        val before = now.minusDays(1).withHour(23)
        assertTrue(widgetStatus("Yo", listOf(shift), emptyList(), before).detail.contains("mañana"))
        val after = now.withHour(1)
        assertEquals("Turno", widgetStatus("Yo", listOf(shift), emptyList(), after).title)
        assertFalse(widgetStatus("Yo", listOf(shift), emptyList(), after).detail.contains("mañana"))
        assertEquals("Libre", widgetStatus("Yo", listOf(shift), emptyList(), now.withHour(7)).title)
    }

    @Test fun upcomingActivityWrapsTheWeek() {
        val sunday = now.minusDays(1).withHour(23)
        assertTrue(widgetStatus("Yo", listOf(lesson), emptyList(), sunday).next.contains("mañana"))
    }

    @Test fun emptyScheduleShowsFreeWithoutInventingAnActivity() {
        val state = widgetStatus("Yo", emptyList(), emptyList(), now)
        assertEquals("Libre", state.title)
        assertEquals("Libre · sin actividades programadas", state.next)
    }
}
