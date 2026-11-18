package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class ScheduleOccurrence(val schedule: ScheduleDto, val originalDate: LocalDate, val window: ScheduleWindow) {
    val date: LocalDate get() = window.start.toLocalDate()
}

/** Dates are interpreted in the schedule owner's zone. Overnight occurrences keep their original start date. */
fun ScheduleDto.occurrences(from: LocalDate, through: LocalDate, zone: ZoneId): Sequence<ScheduleOccurrence> {
    if (from.isAfter(through)) return emptySequence()
    val first = from.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(dayOfWeek)))
    val recurring = generateSequence(first) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(through) }
        .filterNot { occurrenceExceptions.containsKey(it.toString()) }
        .mapNotNull { occurrence(it, it, zone) }
    val moved = occurrenceExceptions.asSequence().mapNotNull { (original, exception) ->
        val originalDate = runCatching { LocalDate.parse(original) }.getOrNull() ?: return@mapNotNull null
        val date = exception.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        if (date.isBefore(from) || date.isAfter(through) || exception.cancelled) return@mapNotNull null
        copy(
            dayOfWeek = date.dayOfWeek.value,
            startTime = exception.startTime ?: return@mapNotNull null,
            endTime = exception.endTime ?: return@mapNotNull null,
            endsNextDay = exception.endsNextDay
        ).occurrence(originalDate, date, zone)
    }
    return recurring + moved
}

private fun ScheduleDto.occurrence(original: LocalDate, date: LocalDate, zone: ZoneId): ScheduleOccurrence? =
    windowOn(date, zone)?.let { ScheduleOccurrence(this, original, it) }

fun List<ScheduleDto>.occurrencesOn(date: LocalDate, zone: ZoneId): List<ScheduleOccurrence> =
    asSequence().flatMap { it.occurrences(date.minusDays(1), date, zone) }
        .filter { it.window.end.isAfter(date.atStartOfDay(zone)) }
        .sortedBy { it.window.start.toInstant() }
        .toList()

/** An exception can suppress at most one weekly start. Include an extra week if today's start passed. */
fun ScheduleDto.nextOccurrence(after: ZonedDateTime): ScheduleOccurrence? {
    val from = after.toLocalDate()
    val lastRecurring = from.plusWeeks(occurrenceExceptions.size.toLong() + 1)
    return occurrences(from, lastRecurring, after.zone)
        .filter { it.window.start.isAfter(after) }
        .minByOrNull { it.window.start.toInstant() }
}
