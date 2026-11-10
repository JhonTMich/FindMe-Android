package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.ZonedDateTime

/** Only starts later on the person's local date; active and yesterday's overnight rows are excluded. */
fun nextActivityToday(schedules: List<ScheduleDto>, now: ZonedDateTime): ScheduleDto? = schedules
    .filter { it.dayOfWeek == now.dayOfWeek.value }
    .mapNotNull { activity ->
        val start = runCatching {
            now.toLocalDate().atStartOfDay().plusNanos(scheduleTimeNanos(activity.startTime)).atZone(now.zone)
        }.getOrNull() ?: return@mapNotNull null
        if (start.toLocalDate() == now.toLocalDate() && start.isAfter(now)) activity to start else null
    }
    .minWithOrNull(compareBy<Pair<ScheduleDto, ZonedDateTime>> { it.second }.thenBy { it.first.id })?.first
