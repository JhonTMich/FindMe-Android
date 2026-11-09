package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val twelveHourFormat = DateTimeFormatter.ofPattern("h:mm", Locale.ROOT)

fun formatScheduleTime(value: String): String {
    val time = if (value.startsWith("24:00")) LocalTime.MIDNIGHT
        else runCatching { LocalTime.parse(value) }.getOrNull() ?: return value
    val period = if (time.hour < 12) "a. m." else "p. m."
    return "${time.format(twelveHourFormat)} $period"
}

fun ScheduleDto.timeRangeLabel(displayDay: Int = dayOfWeek): String {
    val start = formatScheduleTime(startTime)
    val end = formatScheduleTime(endTime)
    return when {
        continuesOn(displayDay) -> "Desde ayer $start · hasta $end"
        endsNextDay -> "$start — $end (+1 día)"
        else -> "$start — $end"
    }
}
