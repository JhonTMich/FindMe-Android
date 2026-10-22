package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto

sealed interface UserCurrentStatus {
    val displayName: String
    val selectedDayOfWeek: Int
    val todaySchedules: List<ScheduleDto>

    data class Override(
        override val displayName: String,
        val customLocation: String,
        val customMessage: String?,
        val expiresAt: String,
        override val selectedDayOfWeek: Int,
        override val todaySchedules: List<ScheduleDto>
    ) : UserCurrentStatus

    data class InClass(
        override val displayName: String,
        val currentSchedule: ScheduleDto,
        val remainingMinutes: Long,
        val progress: Float, // 0.0f to 1.0f
        override val selectedDayOfWeek: Int,
        override val todaySchedules: List<ScheduleDto>
    ) : UserCurrentStatus

    data class FreeTime(
        override val displayName: String,
        override val selectedDayOfWeek: Int,
        override val todaySchedules: List<ScheduleDto>
    ) : UserCurrentStatus

    data class Loading(
        override val displayName: String = "Usuario",
        override val selectedDayOfWeek: Int = 1,
        override val todaySchedules: List<ScheduleDto> = emptyList()
    ) : UserCurrentStatus

    data class Error(
        val message: String,
        override val displayName: String = "Usuario",
        override val selectedDayOfWeek: Int = 1,
        override val todaySchedules: List<ScheduleDto> = emptyList()
    ) : UserCurrentStatus
}
