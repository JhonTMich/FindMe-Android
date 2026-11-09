package com.example.findme.widget

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.activeWindow
import com.example.findme.domain.model.formatScheduleTime
import java.time.ZonedDateTime

data class WidgetStatus(val title: String, val detail: String, val next: String, val kind: String)

fun widgetStatus(name: String, schedules: List<ScheduleDto>, statuses: List<StatusOverrideDto>, now: ZonedDateTime): WidgetStatus {
    val state = StatusRepository.calculateStatus(name, schedules, statuses, now.dayOfWeek.value, now)
    val upcoming = schedules.mapNotNull { schedule ->
        StatusRepository.parseLocalTimeOrNull(schedule.startTime)?.let { time ->
            var start = now.toLocalDate().plusDays(((schedule.dayOfWeek - now.dayOfWeek.value + 7) % 7).toLong())
                .atTime(time).atZone(now.zone)
            if (!start.isAfter(now)) start = start.plusWeeks(1)
            schedule to start
        }
    }.minByOrNull { it.second }
    val next = upcoming?.let { (schedule, start) ->
        val day = if (start.toLocalDate() == now.toLocalDate()) "" else
            if (start.toLocalDate() == now.toLocalDate().plusDays(1)) "mañana " else
                start.format(java.time.format.DateTimeFormatter.ofPattern("EEE ", java.util.Locale.forLanguageTag("es")))
        "${schedule.title} · $day${formatScheduleTime(schedule.startTime)}"
    } ?: "Libre · sin actividades programadas"
    return when (state) {
        is UserCurrentStatus.InClass -> {
            val end = state.currentSchedule.activeWindow(now)?.end
            val tomorrow = if (end?.toLocalDate()?.isAfter(now.toLocalDate()) == true) " de mañana" else ""
            WidgetStatus(state.currentSchedule.title,
                "Hasta ${formatScheduleTime(state.currentSchedule.endTime)}$tomorrow" +
                    state.currentSchedule.location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), next,
                if (state.currentSchedule.category == "work") "work" else "activity")
        }
        is UserCurrentStatus.Override -> WidgetStatus(state.customLocation,
            state.customMessage?.takeIf { it.isNotBlank() } ?: "Estado compartido", next, "shared")
        else -> WidgetStatus("Libre", "Sin actividad en este momento", next, "free")
    }
}
