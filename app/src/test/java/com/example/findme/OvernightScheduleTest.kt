package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class OvernightScheduleTest {
    private val night = ScheduleDto("night", "own", 3, "19:00", "07:00", "Turno nocturno", "", "work", endsNextDay = true)
    private val thursday = ZonedDateTime.parse("2026-11-12T01:00:00-05:00[America/Lima]")

    @Test fun nightShiftRemainsActiveAfterMidnightWithCorrectProgress() {
        val status = StatusRepository.calculateStatus("Persona", listOf(night), emptyList(), 4, thursday)
        assertTrue(status is UserCurrentStatus.InClass)
        status as UserCurrentStatus.InClass
        assertEquals("night", status.currentSchedule.id)
        assertEquals(360L, status.remainingMinutes)
        assertEquals(.5f, status.progress, .0001f)
        assertEquals(listOf(night), status.todaySchedules)
        assertNull(night.activeWindow(thursday.withHour(7)))
        assertNotNull(night.activeWindow(thursday.minusDays(1).withHour(19)))
        assertNull(night.activeWindow(thursday.minusDays(1).withHour(18)))
    }

    @Test fun sundayShiftContinuesIntoMondayAndPreservesTheOriginalRow() {
        val sunday = night.copy(dayOfWeek = 7)
        val monday = ZonedDateTime.parse("2026-11-16T06:59:59-05:00[America/Lima]")
        assertNotNull(sunday.activeWindow(monday))
        assertNull(sunday.activeWindow(monday.withHour(7).withMinute(0).withSecond(0)))
        assertEquals(listOf(sunday), listOf(sunday).schedulesForDay(1))
        assertEquals(listOf(sunday), listOf(sunday).schedulesForDay(7))
    }

    @Test fun conflictsCoverBothDaysAndKeepOnlyTheVirtualClassException() {
        val morning = night.copy(id = "morning", dayOfWeek = 4, startTime = "06:00", endTime = "08:00",
            category = "university", endsNextDay = false)
        assertEquals(night, findScheduleConflict(listOf(night), morning))
        assertNull(findScheduleConflict(listOf(night), morning.copy(modality = "virtual")))
        assertEquals(morning.copy(modality = "virtual"),
            findScheduleConflict(listOf(morning.copy(modality = "virtual")),
                night.copy(id = "class", category = "university", modality = "virtual")))
        assertNull(findScheduleConflict(listOf(night), morning.copy(startTime = "07:00")))
        assertNull(findScheduleConflict(listOf(night), night))
        val sunday = night.copy(dayOfWeek = 7)
        assertEquals(sunday, findScheduleConflict(listOf(sunday), morning.copy(dayOfWeek = 1)))
    }

    @Test fun overnightFlagIsRequiredAndShiftsAreLimitedToTwentyFourHours() {
        assertNotNull(validateSchedule(3, "19:00", "07:00", "Turno"))
        assertNull(validateSchedule(3, "19:00", "07:00", "Turno", true))
        assertNull(validateSchedule(3, "07:00", "07:00", "Turno", true))
        assertNotNull(validateSchedule(3, "07:00", "19:00", "Turno", true))
        assertNull(validateSchedule(3, "19:00", "24:00:00", "Turno"))
    }

    @Test fun midnightEndDoesNotAddAnEmptyActivityToTheNextDay() {
        val midnight = night.copy(endTime = "00:00")
        assertTrue(listOf(midnight).schedulesForDay(4).isEmpty())
        assertEquals(listOf(midnight), listOf(midnight).schedulesForDay(3))
    }
}
