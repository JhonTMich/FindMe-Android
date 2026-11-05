package com.example.findme

import com.example.findme.domain.model.validateSchedule
import com.example.findme.domain.model.formatScheduleTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleValidationTest {
    @Test fun displaysNoonAndMidnightInTwelveHourFormat() {
        assertEquals("12:00 a. m.", formatScheduleTime("00:00"))
        assertEquals("12:00 p. m.", formatScheduleTime("12:00:00"))
        assertEquals("9:01 p. m.", formatScheduleTime("21:01:00.000000"))
        assertEquals("12:00 a. m.", formatScheduleTime("24:00:00"))
    }

    @Test fun acceptsAValidSameDayActivity() {
        assertNull(validateSchedule(1, "09:00", "10:30", "Trabajo"))
        assertNull(validateSchedule(7, "00:00", "23:59", "Actividad"))
    }

    @Test fun rejectsInvalidDaysAndMissingTitle() {
        assertNotNull(validateSchedule(0, "09:00", "10:00", "Actividad"))
        assertNotNull(validateSchedule(8, "09:00", "10:00", "Actividad"))
        assertNotNull(validateSchedule(1, "09:00", "10:00", "  "))
        assertNotNull(validateSchedule(1, "09:00", "10:00", "a".repeat(121)))
    }

    @Test fun rejectsMalformedTimesAndUnsupportedOvernightIntervals() {
        assertNotNull(validateSchedule(1, "25:00", "26:00", "Actividad"))
        assertNotNull(validateSchedule(1, "9am", "10:00", "Actividad"))
        assertNotNull(validateSchedule(1, "10:00", "10:00", "Actividad"))
        assertNotNull(validateSchedule(1, "23:00", "01:00", "Actividad"))
    }
}
