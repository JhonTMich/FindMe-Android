package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleException(
    val cancelled: Boolean = false,
    val date: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("ends_next_day") val endsNextDay: Boolean = false
)
