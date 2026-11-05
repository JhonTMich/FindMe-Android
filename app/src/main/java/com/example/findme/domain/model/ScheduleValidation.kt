package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.LocalTime

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
    existingId: String?,
    day: Int,
    start: String,
    end: String,
    category: String,
    modality: String
): ScheduleDto? {
    val startTime = LocalTime.parse(start.trim())
    val endTime = LocalTime.parse(end.trim())
    val virtualClass = category == "university" && modality == "virtual"
    return schedules.firstOrNull { other ->
        if (other.id == existingId || other.dayOfWeek != day) return@firstOrNull false
        val otherVirtualClass = other.category == "university" && other.modality == "virtual"
        if ((virtualClass && other.category == "work") || (category == "work" && otherVirtualClass)) {
            return@firstOrNull false
        }
        val otherStart = LocalTime.parse(other.startTime)
        val otherEnd = if (other.endTime.startsWith("24:")) LocalTime.MAX else LocalTime.parse(other.endTime)
        startTime.isBefore(otherEnd) && otherStart.isBefore(endTime)
    }
}

fun ScheduleDto.conflictMessage(): String =
    "Coincide con «$title» (${startTime.take(5)}–${endTime.take(5)}). Cambia el horario o el día."
