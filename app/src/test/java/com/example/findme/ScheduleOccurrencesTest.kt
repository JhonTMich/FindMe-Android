package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.nextActivityToday
import com.example.findme.domain.model.nextOccurrence
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.widget.widgetStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleOccurrencesTest {
    private val lima = ZoneId.of("America/Lima")
    private val monday = LocalDate.parse("2026-11-16")
    private val weekly = ScheduleDto("class", dayOfWeek = 1, startTime = "09:00", endTime = "10:00",
        title = "Clase", location = "")

    @Test fun cancellationOnlyAppliesOnItsDate() {
        val cancelled = weekly.copy(occurrenceExceptions = mapOf(monday.toString() to ScheduleException(cancelled = true)))
        val now = monday.atTime(9, 15).atZone(lima)
        assertNull(nextActivityToday(listOf(cancelled), monday.atStartOfDay(lima)))
        assertTrue(StatusRepository.calculateStatus("Ana", listOf(cancelled), emptyList(), 1, now) is UserCurrentStatus.FreeTime)
        assertEquals(monday.plusWeeks(1), cancelled.nextOccurrence(now)?.date)
        assertTrue(widgetStatus("Ana", listOf(cancelled), emptyList(), now).next.contains("Clase"))
        assertEquals(1, listOf(cancelled).occurrencesOn(monday.plusWeeks(1), lima).size)
    }

    @Test fun movingAnOvernightOccurrenceChangesBothDates() {
        val tuesday = monday.plusDays(1)
        val moved = weekly.copy(occurrenceExceptions = mapOf(monday.toString() to ScheduleException(
            date = tuesday.toString(), startTime = "23:00", endTime = "02:00", endsNextDay = true
        )))
        assertTrue(listOf(moved).occurrencesOn(monday, lima).isEmpty())
        val wednesday = tuesday.plusDays(1)
        val continuation = listOf(moved).occurrencesOn(wednesday, lima).single()
        assertEquals(monday, continuation.originalDate)
        assertEquals(wednesday.atTime(2, 0).atZone(lima), continuation.window.end)
        assertTrue(StatusRepository.calculateStatus("Ana", listOf(moved), emptyList(), 3,
            wednesday.atTime(1, 0).atZone(lima)) is UserCurrentStatus.InClass)
        assertNull(nextActivityToday(listOf(moved), wednesday.atStartOfDay(lima)))
    }

    @Test fun anOccurrenceCanMoveIntoTheWindowFromAnotherWeek() {
        val friday = monday.minusDays(3)
        val moved = weekly.copy(occurrenceExceptions = mapOf(monday.toString() to ScheduleException(
            date = friday.toString(), startTime = "15:00", endTime = "16:00"
        )))
        assertEquals(friday, moved.nextOccurrence(ZonedDateTime.of(friday.atStartOfDay(), lima))?.date)
        assertEquals(5, nextActivityToday(listOf(moved), friday.atStartOfDay(lima))?.dayOfWeek)
    }
}
