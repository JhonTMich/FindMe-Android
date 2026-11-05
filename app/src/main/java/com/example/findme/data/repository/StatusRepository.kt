package com.example.findme.data.repository

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.domain.model.UserCurrentStatus
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
        val todayLimaDay = nowInLima.dayOfWeek.value
        val currentTime = nowInLima.toLocalTime()

        val daySchedules = schedules
            .filter { it.dayOfWeek == selectedDayOfWeek }
            .sortedBy { parseLocalTimeOrNull(it.startTime) ?: LocalTime.MIN }

        // Find an unexpired manual status.
        val activeOverride = overrides
            .filter { it.isActive }
            .firstOrNull { overrideDto ->
                val expiresInstant = parseIsoToInstant(overrideDto.expiresAt)
                expiresInstant != null && expiresInstant.isAfter(nowInLima.toInstant())
            }

        val todayActualSchedules = schedules
            .filter { it.dayOfWeek == todayLimaDay }
            .sortedBy { parseLocalTimeOrNull(it.startTime) ?: LocalTime.MIN }

        val currentSchedule = todayActualSchedules.firstOrNull { schedule ->
            val start = parseLocalTimeOrNull(schedule.startTime)
            val end = parseLocalTimeOrNull(schedule.endTime)
            if (start != null && end != null) {
                !currentTime.isBefore(start) && currentTime.isBefore(end)
            } else false
        }

        // Account users can explicitly share a change of plans, even during a scheduled activity.
        if (activeOverride != null) {
            return UserCurrentStatus.Override(displayName, activeOverride.customLocation, activeOverride.customMessage,
                activeOverride.expiresAt, selectedDayOfWeek = selectedDayOfWeek, todaySchedules = daySchedules)
        }

        // An activity is active from its start time (inclusive) until its end (exclusive).
        if (currentSchedule != null) {
            val start = parseLocalTimeOrNull(currentSchedule.startTime) ?: currentTime
            val end = parseLocalTimeOrNull(currentSchedule.endTime) ?: currentTime
            val totalSec = Duration.between(start, end).seconds.coerceAtLeast(1)
            val elapsedSec = Duration.between(start, currentTime).seconds.coerceAtLeast(0)
            val remainingMin = (Duration.between(currentTime, end).seconds / 60).coerceAtLeast(0)
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
