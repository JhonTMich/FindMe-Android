package com.example.findme.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.findme.data.model.ScheduleDto

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val title: String,
    val location: String,
    val category: String,
    val modality: String,
)

fun ScheduleEntity.toDto(): ScheduleDto {
    return ScheduleDto(
        id = id,
        userId = userId,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        title = title,
        location = location,
        category = category,
        modality = modality
    )
}

fun ScheduleDto.toEntity(fallbackUserId: String = ""): ScheduleEntity {
    val targetUserId = if (!userId.isNullOrEmpty()) userId else fallbackUserId
    val assignedId = if (id.isNotEmpty()) id else "${targetUserId}_${dayOfWeek}_${startTime}_${title}"

    return ScheduleEntity(
        id = assignedId,
        userId = targetUserId,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        title = title,
        location = location,
        category = category,
        modality = modality
    )
}
