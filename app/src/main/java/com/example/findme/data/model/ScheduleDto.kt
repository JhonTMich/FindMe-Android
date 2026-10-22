package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("day_of_week") val dayOfWeek: Int, // 1 = Lunes ... 7 = Domingo
    @SerialName("start_time") val startTime: String, // "HH:MM:SS"
    @SerialName("end_time") val endTime: String,   // "HH:MM:SS"
    val title: String,
    val location: String
)
