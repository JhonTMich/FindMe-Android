package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class CurrentStatusTest {
    private val monday = ZonedDateTime.parse("2026-10-26T10:30:00-05:00")
    private val activity = ScheduleDto(id = "monday", dayOfWeek = 1, startTime = "10:00:00",
        endTime = "11:00:00", title = "Actividad real", location = "Aula", category = "university", modality = "Presencial")
    @Test fun currentActivityUsesActualDayWhenBrowsingAnotherDay() {
        val state = StatusRepository.calculateStatus("Persona", listOf(activity), emptyList(), 2, monday)
        assertTrue(state is UserCurrentStatus.InClass)
        assertTrue(state.todaySchedules.isEmpty())
    }
    @Test fun emptyScheduleDoesNotCreateActivities() {
        val state = StatusRepository.calculateStatus("Persona", emptyList(), emptyList(), 1, monday)
        assertTrue(state is UserCurrentStatus.FreeTime)
        assertTrue(state.todaySchedules.isEmpty())
    }
    @Test fun activityEndsAtTheEndBoundary() {
        val state = StatusRepository.calculateStatus("Persona", listOf(activity), emptyList(), 1, monday.withHour(11).withMinute(0))
        assertTrue(state is UserCurrentStatus.FreeTime)
    }
    private val thursday = ZonedDateTime.parse("2026-10-29T17:00:00-05:00[America/Lima]")
    private val longActivity = activity.copy(id = "thursday", dayOfWeek = 4,
        startTime = "09:00:00", endTime = "18:00:00", title = "Actividad del jueves")
    private val travel = StatusOverrideDto(isActive = true, customLocation = "En camino a casa",
        expiresAt = "2026-10-29T23:30:00Z")

    @Test fun thursdayAtFiveShowsActivityDespiteManualTravelAnnouncement() {
        val state = StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(travel), 4, thursday)
        assertTrue(state is UserCurrentStatus.InClass)
        state as UserCurrentStatus.InClass
        assertEquals("thursday", state.currentSchedule.id)
        assertEquals(60L, state.remainingMinutes)
        assertEquals(8f / 9f, state.progress, .0001f)
    }
    @Test fun activityHasPriorityFromNineUntilJustBeforeSix() {
        for (time in listOf(thursday.withHour(9), thursday.withHour(17).withMinute(59).withSecond(59))) {
            assertTrue(StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(travel), 4, time) is UserCurrentStatus.InClass)
        }
        val ended = StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(travel), 4, thursday.withHour(18))
        assertTrue(ended is UserCurrentStatus.Override)
        assertEquals("En camino a casa", (ended as UserCurrentStatus.Override).customLocation)
    }
    @Test fun expiredTravelAnnouncementIsNotShownAfterActivity() {
        val state = StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(travel), 4, thursday.withHour(19))
        assertTrue(state is UserCurrentStatus.FreeTime)
    }
    @Test fun postgresFractionalSecondsAreReadAsLocalTimes() {
        val withFractions = longActivity.copy(startTime = "09:00:00.000000", endTime = "18:00:00.000000")
        assertTrue(StatusRepository.calculateStatus("Persona", listOf(withFractions), listOf(travel), 4, thursday) is UserCurrentStatus.InClass)
        assertEquals(java.time.LocalTime.of(9, 0), StatusRepository.parseLocalTimeOrNull("9:00"))
        assertEquals(java.time.LocalTime.of(9, 0, 1, 123456000), StatusRepository.parseLocalTimeOrNull("09:00:01.123456"))
        assertNull(StatusRepository.parseLocalTimeOrNull("25:00:00"))
        assertNull(StatusRepository.parseLocalTimeOrNull("invalid"))
    }

    @Test fun accountStatusOverridesAnActivityUntilItExpires() {
        val state = StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(travel), 4,
            thursday, manualStatusFirst = true)
        assertTrue(state is UserCurrentStatus.Override)
        assertEquals("En camino a casa", (state as UserCurrentStatus.Override).customLocation)
        assertEquals(listOf(longActivity), state.todaySchedules)
        val expired = travel.copy(expiresAt = thursday.toInstant().toString())
        assertTrue(StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(expired), 4,
            thursday, manualStatusFirst = true) is UserCurrentStatus.InClass)
    }

    @Test fun accountStatusCanShowTravelAfterClassAndReturnToScheduleWhenRemoved() {
        val afterClass = thursday.withHour(18).withMinute(10)
        assertTrue(StatusRepository.calculateStatus("Persona", listOf(longActivity), listOf(travel), 4,
            afterClass, manualStatusFirst = true) is UserCurrentStatus.Override)
        assertTrue(StatusRepository.calculateStatus("Persona", listOf(longActivity), emptyList(), 4,
            thursday, manualStatusFirst = true) is UserCurrentStatus.InClass)
    }

}
