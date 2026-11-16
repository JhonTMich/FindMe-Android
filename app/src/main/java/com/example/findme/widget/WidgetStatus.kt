package com.example.findme.widget

import com.example.findme.data.model.ActivityCategory
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.activeWindow
import com.example.findme.domain.model.formatScheduleTime
import java.time.ZonedDateTime

/** The three illustrated scenes the widget can show; each one matches an activity category of the app. */
enum class WidgetScene { UNIVERSITY, WORK, FREE }

data class WidgetStatus(
    val title: String,
    val detail: String,
    val next: String,
    val scene: WidgetScene,
    /** Short headline that describes the scene, e.g. "En clase". */
    val label: String,
    val shared: Boolean = false
)

private val workWords = listOf("trabaj", "oficina", "reunión", "reunion", "turno", "chamba")
private val studyWords = listOf("clase", "universidad", "facultad", "biblioteca", "estudi", "examen", "curso", "aula", "laboratorio")

/** Shared statuses are free text, so their scene is inferred from the words the person used. */
internal fun sceneForSharedStatus(text: String): WidgetScene {
    val clean = text.lowercase()
    return when {
        workWords.any { it in clean } -> WidgetScene.WORK
        studyWords.any { it in clean } -> WidgetScene.UNIVERSITY
        else -> WidgetScene.FREE
    }
}

private fun ScheduleDto.scene() = when (ActivityCategory.fromKey(category, title)) {
    ActivityCategory.WORK -> WidgetScene.WORK
    ActivityCategory.FREE -> WidgetScene.FREE
    ActivityCategory.UNIVERSITY -> WidgetScene.UNIVERSITY
}

private fun ScheduleDto.label() = when (scene()) {
    WidgetScene.WORK -> "Trabajando"
    WidgetScene.FREE -> "Tiempo libre"
    WidgetScene.UNIVERSITY -> if (modality == "virtual") "En clase virtual" else "En clase"
}

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
            val schedule = state.currentSchedule
            val end = schedule.activeWindow(now)?.end
            val tomorrow = if (end?.toLocalDate()?.isAfter(now.toLocalDate()) == true) " de mañana" else ""
            WidgetStatus(schedule.title,
                "Hasta ${formatScheduleTime(schedule.endTime)}$tomorrow" +
                    schedule.location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), next,
                schedule.scene(), schedule.label())
        }
        is UserCurrentStatus.Override -> WidgetStatus(state.customLocation,
            state.customMessage?.takeIf { it.isNotBlank() } ?: "Estado compartido", next,
            sceneForSharedStatus("${state.customLocation} ${state.customMessage.orEmpty()}"), "Estado compartido", shared = true)
        else -> WidgetStatus("Libre", "Sin actividad en este momento", next, WidgetScene.FREE, "Tiempo libre")
    }
}
