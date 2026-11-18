package com.example.findme.domain.model

import com.example.findme.data.model.SharedSchedules
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class FreeSlot(val start: ZonedDateTime, val end: ZonedDateTime)
data class FreeDay(val date: LocalDate, val slots: List<FreeSlot>)

/** Compare the real instants of both schedules before grouping the gaps in the viewer's calendar. */
fun commonFreeTime(
    schedules: SharedSchedules,
    now: Instant,
    allHours: Boolean = false,
    minimumMinutes: Long = 30
): List<FreeDay> {
    val viewerZone = ZoneId.of(schedules.viewerZone)
    val contactZone = ZoneId.of(schedules.contactZone)
    val firstDay = now.atZone(viewerZone).toLocalDate()
    val through = firstDay.plusDays(7).atStartOfDay(viewerZone).toInstant()
    val firstMinute = now.truncatedTo(ChronoUnit.MINUTES).let { if (it < now) it.plusSeconds(60) else it }
    val busy = (schedules.viewer.flatMap { row ->
        row.occurrences(firstDay.minusDays(1), firstDay.plusDays(6), viewerZone).toList()
    } + schedules.contact.flatMap { row ->
        val start = now.atZone(contactZone).toLocalDate().minusDays(1)
        val end = through.atZone(contactZone).toLocalDate()
        row.occurrences(start, end, contactZone).toList()
    }).map { it.window.start.toInstant() to it.window.end.toInstant() }
        .sortedBy { it.first }

    return (0L..6L).map { offset ->
        val date = firstDay.plusDays(offset)
        val rangeStart = if (allHours) date.atStartOfDay(viewerZone) else date.atTime(8, 0).atZone(viewerZone)
        val rangeEnd = if (allHours) date.plusDays(1).atStartOfDay(viewerZone)
            else date.atTime(LocalTime.of(22, 0)).atZone(viewerZone)
        val dayEnd = rangeEnd.toInstant()
        val slots = mutableListOf<FreeSlot>()
        var cursor = maxOf(firstMinute, rangeStart.toInstant())
        fun addSlot(end: Instant) {
            if (Duration.between(cursor, end).toMinutes() >= minimumMinutes) {
                slots += FreeSlot(cursor.atZone(viewerZone), end.atZone(viewerZone))
            }
        }
        for ((start, end) in busy) {
            if (end <= cursor) continue
            if (start >= dayEnd) break
            addSlot(start)
            cursor = maxOf(cursor, end)
            if (cursor >= dayEnd) break
        }
        addSlot(dayEnd)
        FreeDay(date, slots)
    }
}
