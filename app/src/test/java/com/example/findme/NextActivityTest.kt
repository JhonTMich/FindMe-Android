package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.nextActivityToday
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextActivityTest {
    private val now = ZonedDateTime.parse("2026-11-10T10:00:00-05:00[America/Lima]")
    private val activity = ScheduleDto("next", "own", 2, "12:00", "13:00", "Almuerzo", "Casa")

    @Test fun picksEarliestFutureStartRegardlessOfInputOrder() {
        val later = activity.copy(id = "late", startTime = "18:00", endTime = "19:00")
        assertEquals(activity, nextActivityToday(listOf(later, activity), now))
    }

    @Test fun excludesCurrentAndFinishedActivitiesAtTheExactStart() {
        assertNull(nextActivityToday(listOf(activity), now.withHour(12)))
        assertNull(nextActivityToday(listOf(activity), now.withHour(14)))
    }

    @Test fun doesNotShowTomorrowOrNextWeeksActivities() {
        assertNull(nextActivityToday(listOf(activity.copy(dayOfWeek = 3)), now))
        assertNull(nextActivityToday(listOf(activity), now.withHour(23)))
    }

    @Test fun overnightShiftFromYesterdayIsNotUpcomingToday() {
        val night = activity.copy(dayOfWeek = 1, startTime = "19:00", endTime = "11:00", endsNextDay = true)
        assertEquals(activity, nextActivityToday(listOf(night, activity), now))
        assertNull(nextActivityToday(listOf(night), now))
    }

    @Test fun tonightShiftIsUpcomingEvenWhenItEndsTomorrow() {
        val night = activity.copy(startTime = "19:00", endTime = "07:00", endsNextDay = true)
        assertEquals(night, nextActivityToday(listOf(night), now))
    }

    @Test fun usesTheProvidedLocalDateAndTimezone() {
        val lima = now.withHour(23)
        val tokyo = lima.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        val localTomorrow = activity.copy(dayOfWeek = 3, startTime = "15:00", endTime = "16:00")
        assertNull(nextActivityToday(listOf(localTomorrow), lima))
        assertEquals(localTomorrow, nextActivityToday(listOf(localTomorrow), tokyo))
    }

    @Test fun overlappingVirtualActivityIsStillAnUpcomingStart() {
        val work = activity.copy(startTime = "08:00", endTime = "18:00", category = "work")
        assertEquals(activity, nextActivityToday(listOf(work, activity), now))
    }

    @Test fun ignoresInvalidTimesAndEmptyDays() {
        assertNull(nextActivityToday(emptyList(), now))
        assertEquals(activity, nextActivityToday(listOf(activity.copy(startTime = "bad"), activity), now))
    }
}
