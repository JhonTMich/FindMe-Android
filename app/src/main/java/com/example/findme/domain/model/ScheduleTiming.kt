package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.LocalTime
import java.time.ZonedDateTime

private const val DAY_NANOS = 86_400_000_000_000L
private const val WEEK_NANOS = DAY_NANOS * 7
private val endOfDay = Regex("24:00(?::00(?:\\.0+)?)?")

internal fun scheduleTimeNanos(value: String): Long =
    if (endOfDay.matches(value.trim())) DAY_NANOS else LocalTime.parse(value.trim()).toNanoOfDay()

fun ScheduleDto.continuesOn(day: Int): Boolean =
    endsNextDay && day == dayOfWeek % 7 + 1 && runCatching { scheduleTimeNanos(endTime) > 0 }.getOrDefault(false)

fun ScheduleDto.occursOn(day: Int): Boolean = dayOfWeek == day || continuesOn(day)

fun List<ScheduleDto>.schedulesForDay(day: Int): List<ScheduleDto> =
    filter { it.occursOn(day) }
        .sortedBy { if (it.continuesOn(day)) 0L else runCatching { scheduleTimeNanos(it.startTime) }.getOrDefault(0) }

private data class WeeklySpan(val start: Long, val end: Long) {
    fun overlaps(other: WeeklySpan) = start < other.end && other.start < end
}

private fun ScheduleDto.weeklySpans(): List<WeeklySpan> {
    val dayStart = (dayOfWeek - 1) * DAY_NANOS
    val start = dayStart + scheduleTimeNanos(startTime)
    val end = dayStart + scheduleTimeNanos(endTime) + if (endsNextDay) DAY_NANOS else 0
    return if (end > WEEK_NANOS) {
        listOf(WeeklySpan(start, WEEK_NANOS), WeeklySpan(0, end - WEEK_NANOS))
    } else listOf(WeeklySpan(start, end))
}

internal fun ScheduleDto.overlaps(other: ScheduleDto): Boolean =
    weeklySpans().any { span -> other.weeklySpans().any(span::overlaps) }

data class ScheduleWindow(val start: ZonedDateTime, val end: ZonedDateTime)

fun ScheduleDto.activeWindow(now: ZonedDateTime): ScheduleWindow? {
    val startNanos = runCatching { scheduleTimeNanos(startTime) }.getOrNull() ?: return null
    val endNanos = runCatching { scheduleTimeNanos(endTime) }.getOrNull() ?: return null
    val date = when {
        dayOfWeek == now.dayOfWeek.value -> now.toLocalDate()
        continuesOn(now.dayOfWeek.value) -> now.toLocalDate().minusDays(1)
        else -> return null
    }
    val start = date.atStartOfDay().plusNanos(startNanos).atZone(now.zone)
    val end = date.atStartOfDay().plusDays(if (endsNextDay) 1 else 0)
        .plusNanos(endNanos).atZone(now.zone)
    return if (!now.isBefore(start) && now.isBefore(end)) ScheduleWindow(start, end) else null
}
