package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.model.SharedSchedules
import com.example.findme.domain.model.commonFreeTime
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class CommonFreeTimeTest {
    private val monday = Instant.parse("2026-11-16T13:00:00Z")
    private fun activity(id: String, day: Int, start: String, end: String, overnight: Boolean = false) =
        ScheduleDto(id, dayOfWeek = day, startTime = start, endTime = end, title = id, location = "", endsNextDay = overnight)

    @Test fun combinesOverlappingEventsInDifferentZones() {
        val pair = SharedSchedules("America/Lima", "Europe/Madrid",
            listOf(activity("own", 1, "09:00", "10:00")),
            listOf(activity("contact", 1, "15:30", "17:00")))
        val days = commonFreeTime(pair, monday)
        assertEquals(7, days.size)
        val gaps = days.first().slots.map { it.start.toLocalTime() to it.end.toLocalTime() }
        assertEquals(listOf(LocalTime.of(8, 0) to LocalTime.of(9, 0), LocalTime.of(11, 0) to LocalTime.of(22, 0)), gaps)
    }

    @Test fun respectsCancellationAndPreviousNightAcrossMidnight() {
        val own = activity("own", 1, "09:00", "10:00").copy(
            occurrenceExceptions = mapOf("2026-11-16" to ScheduleException(cancelled = true))
        )
        val pair = SharedSchedules("America/Lima", "America/Lima",
            listOf(own), listOf(activity("night", 7, "21:00", "08:15", true)))
        val slot = commonFreeTime(pair, Instant.parse("2026-11-16T05:00:00Z"), allHours = true).first().slots.single()
        assertEquals(LocalTime.of(8, 15), slot.start.toLocalTime())
        assertEquals("2026-11-17", slot.end.toLocalDate().toString())
    }

    @Test fun clipsTodayAndSkipsShortGaps() {
        val pair = SharedSchedules("America/Lima", "America/Lima",
            listOf(activity("own", 1, "09:00", "10:00")), emptyList())
        val slot = commonFreeTime(pair, Instant.parse("2026-11-16T13:40:01Z")).first().slots.single()
        assertEquals(LocalTime.of(10, 0), slot.start.toLocalTime())
    }
}
