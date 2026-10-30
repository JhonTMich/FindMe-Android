package com.example.findme.domain.model

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
