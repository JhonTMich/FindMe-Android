package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.ZonedDateTime

/** Only starts later on the person's local date; active and yesterday's overnight rows are excluded. */
fun nextActivityToday(schedules: List<ScheduleDto>, now: ZonedDateTime): ScheduleDto? =
    schedules.occurrencesOn(now.toLocalDate(), now.zone).asSequence()
        .filter { it.window.start.isAfter(now) }
        .minWithOrNull(compareBy<ScheduleOccurrence> { it.window.start.toInstant() }.thenBy { it.schedule.id })
        ?.schedule
