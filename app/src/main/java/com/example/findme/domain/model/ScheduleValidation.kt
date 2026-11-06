package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Shared validation for the editor; database constraints remain authoritative. */
fun validateSchedule(day: Int, start: String, end: String, title: String): String? {
    if (day !in 1..7) return "Selecciona un día válido."
    if (title.isBlank() || title.trim().length > 120) return "El título debe tener entre 1 y 120 caracteres."
    val startTime = runCatching { LocalTime.parse(start.trim()) }.getOrNull()
        ?: return "La hora de inicio debe usar el formato HH:mm."
    val endTime = runCatching { LocalTime.parse(end.trim()) }.getOrNull()
        ?: return "La hora de fin debe usar el formato HH:mm."
    if (!endTime.isAfter(startTime)) return "La hora de fin debe ser posterior al inicio, dentro del mismo día."
    return null
}

fun findScheduleConflict(
    schedules: List<ScheduleDto>,
    activity: ScheduleDto
): ScheduleDto? {
    val startTime = LocalTime.parse(activity.startTime.trim())
    val endTime = LocalTime.parse(activity.endTime.trim())
    val virtualClass = activity.category == "university" && activity.modality == "virtual"
    return schedules.firstOrNull { other ->
        if (other.id == activity.id || other.dayOfWeek != activity.dayOfWeek) return@firstOrNull false
        val otherVirtualClass = other.category == "university" && other.modality == "virtual"
        if ((virtualClass && other.category == "work") || (activity.category == "work" && otherVirtualClass)) {
            return@firstOrNull false
        }
        val otherStart = LocalTime.parse(other.startTime)
        val otherEnd = if (other.endTime.startsWith("24:")) LocalTime.MAX else LocalTime.parse(other.endTime)
        startTime.isBefore(otherEnd) && otherStart.isBefore(endTime)
    }
}

fun ScheduleDto.conflictMessage(): String {
    val day = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
    return "Coincide con «$title» el $day (${formatScheduleTime(startTime)}–${formatScheduleTime(endTime)}). Cambia el horario o el día."
}
