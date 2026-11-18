package com.example.findme.data.repository

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.occurrencesOn
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object StatusRepository {
    val SYSTEM_ZONE: ZoneId get() = ZoneId.systemDefault()

    fun calculateStatus(
        displayName: String,
        schedules: List<ScheduleDto>,
        overrides: List<StatusOverrideDto>,
        selectedDayOfWeek: Int,
        nowInLima: ZonedDateTime = ZonedDateTime.now(SYSTEM_ZONE)
    ): UserCurrentStatus {
        val today = nowInLima.toLocalDate()
        val selectedDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(selectedDayOfWeek)))
        val currentOccurrences = schedules.occurrencesOn(today, nowInLima.zone)
        val daySchedules = (if (selectedDate == today) currentOccurrences
            else schedules.occurrencesOn(selectedDate, nowInLima.zone)).map { it.schedule }

        // Find an unexpired manual status.
        val activeOverride = overrides
            .filter { it.isActive }
            .firstOrNull { overrideDto ->
                val expiresInstant = parseIsoToInstant(overrideDto.expiresAt)
                expiresInstant != null && expiresInstant.isAfter(nowInLima.toInstant())
            }

        val active = currentOccurrences.firstOrNull { !nowInLima.isBefore(it.window.start) && nowInLima.isBefore(it.window.end) }

        // Account users can explicitly share a change of plans, even during a scheduled activity.
        if (activeOverride != null) {
            return UserCurrentStatus.Override(displayName, activeOverride.customLocation, activeOverride.customMessage,
                activeOverride.expiresAt, selectedDayOfWeek = selectedDayOfWeek, todaySchedules = daySchedules)
        }

        // An activity is active from its start time (inclusive) until its end (exclusive).
        if (active != null) {
            val currentSchedule = active.schedule
            val window = active.window
            val start = window.start
            val end = window.end
            val totalSec = Duration.between(start, end).seconds.coerceAtLeast(1)
            val elapsedSec = Duration.between(start, nowInLima).seconds.coerceAtLeast(0)
            val remainingMin = (Duration.between(nowInLima, end).seconds / 60).coerceAtLeast(0)
            val progress = (elapsedSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)

            return UserCurrentStatus.InClass(
                displayName = displayName,
                currentSchedule = currentSchedule,
                remainingMinutes = remainingMin,
                progress = progress,
                selectedDayOfWeek = selectedDayOfWeek,
                todaySchedules = daySchedules
            )
        }

        // No scheduled activity or active announcement.
        return UserCurrentStatus.FreeTime(
            displayName = displayName,
            selectedDayOfWeek = selectedDayOfWeek,
            todaySchedules = daySchedules
        )
    }

    fun parseLocalTimeOrNull(timeStr: String): LocalTime? {
        val normalized = timeStr.trim().let { value ->
            // Preserve support for a single-digit hour, while accepting PostgreSQL fractional seconds.
            if (value.indexOf(':') == 1) "0$value" else value
        }
        return try {
            LocalTime.parse(normalized)
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }

    fun parseIsoToInstant(isoString: String): Instant? {
        return try {
            Instant.parse(isoString)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).toInstant()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(isoString).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
