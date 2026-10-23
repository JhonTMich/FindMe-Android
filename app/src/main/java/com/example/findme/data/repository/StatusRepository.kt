package com.example.findme.data.repository

import android.content.Context
import com.example.findme.data.local.AppDatabase
import com.example.findme.data.local.toDto
import com.example.findme.data.model.ProfileDto
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.domain.model.UserCurrentStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object StatusRepository {
    val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")

    suspend fun validateFamilyCode(familyCode: String): Result<ProfileDto> {
        return try {
            val profiles = SupabaseClientProvider.client.postgrest["profiles"]
                .select {
                    filter {
                        eq("family_code", familyCode.trim().uppercase())
                    }
                }
                .decodeList<ProfileDto>()

            val found = profiles.firstOrNull()
            if (found != null) {
                Result.success(found)
            } else {
                Result.failure(Exception("Código familiar no encontrado o no válido"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión al validar código: ${e.message}"))
        }
    }

    suspend fun fetchSchedules(userId: String, context: Context? = null): List<ScheduleDto> {
        return try {
            SupabaseClientProvider.client.postgrest["schedule"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<ScheduleDto>()
        } catch (_: Exception) {
            if (context != null) {
                try {
                    val dao = AppDatabase.getDatabase(context).scheduleDao()
                    dao.getAllSchedulesFlow(userId).first().map { it.toDto() }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun fetchOverrides(userId: String): List<StatusOverrideDto> {
        return try {
            SupabaseClientProvider.client.postgrest["status_override"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<StatusOverrideDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setOnTheWayOverride(userId: String): Result<Unit> {
        return try {
            clearOverrides(userId)
            val expiresAtIso = Instant.now().plusSeconds(45 * 60).toString()
            val override = StatusOverrideDto(
                userId = userId,
                isActive = true,
                customLocation = "En camino a casa",
                customMessage = "De regreso al hogar",
                expiresAt = expiresAtIso
            )
            SupabaseClientProvider.client.postgrest["status_override"].insert(override)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearOverrides(userId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest["status_override"].update(
                {
                    set("is_active", false)
                }
            ) {
                filter {
                    eq("user_id", userId)
                    eq("is_active", true)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeCurrentStatusWithSchedules(
        userId: String,
        displayName: String,
        schedules: List<ScheduleDto>,
        selectedDayOfWeek: Int
    ): Flow<UserCurrentStatus> = flow {
        var cachedOverrides = fetchOverrides(userId)

        emit(calculateStatus(displayName, schedules, cachedOverrides, selectedDayOfWeek))

        try {
            val channel = SupabaseClientProvider.client.realtime.channel("status-repo-$userId") {}
            val realtimeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")
            channel.subscribe()

            coroutineScope {
                launch {
                    realtimeFlow.collect {
                        cachedOverrides = fetchOverrides(userId)
                        emit(
                            calculateStatus(
                                displayName,
                                schedules,
                                cachedOverrides,
                                selectedDayOfWeek
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback en caso de error en Realtime
        }

        while (true) {
            delay(15_000)
            emit(calculateStatus(displayName, schedules, cachedOverrides, selectedDayOfWeek))
        }
    }

    fun observeCurrentStatus(
        userId: String,
        displayName: String,
        selectedDayOfWeek: Int
    ): Flow<UserCurrentStatus> = flow {
        emit(
            UserCurrentStatus.Loading(
                displayName = displayName,
                selectedDayOfWeek = selectedDayOfWeek
            )
        )

        val cachedSchedules = fetchSchedules(userId)
        val cachedOverrides = fetchOverrides(userId)

        emit(calculateStatus(displayName, cachedSchedules, cachedOverrides, selectedDayOfWeek))

        try {
            val channel = SupabaseClientProvider.client.realtime.channel("status-repo-$userId") {}
            val realtimeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")
            channel.subscribe()

            coroutineScope {
                launch {
                    realtimeFlow.collect {
                        val latestSchedules = fetchSchedules(userId)
                        val latestOverrides = fetchOverrides(userId)
                        emit(
                            calculateStatus(
                                displayName,
                                latestSchedules,
                                latestOverrides,
                                selectedDayOfWeek
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback en caso de error en Realtime
        }

        while (true) {
            delay(15_000)
            val latestSchedules = fetchSchedules(userId)
            val latestOverrides = fetchOverrides(userId)
            emit(calculateStatus(displayName, latestSchedules, latestOverrides, selectedDayOfWeek))
        }
    }

    suspend fun getDirectCurrentStatus(
        userId: String,
        displayName: String,
        selectedDayOfWeek: Int = ZonedDateTime.now(LIMA_ZONE).dayOfWeek.value,
        context: Context? = null
    ): UserCurrentStatus {
        val schedules = fetchSchedules(userId, context)
        val overrides = fetchOverrides(userId)
        return calculateStatus(displayName, schedules, overrides, selectedDayOfWeek)
    }

    fun calculateStatus(
        displayName: String,
        schedules: List<ScheduleDto>,
        overrides: List<StatusOverrideDto>,
        selectedDayOfWeek: Int,
        nowInLima: ZonedDateTime = ZonedDateTime.now(LIMA_ZONE)
    ): UserCurrentStatus {
        val todayLimaDay = nowInLima.dayOfWeek.value
        val currentTime = nowInLima.toLocalTime()

        val daySchedules = schedules
            .filter { it.dayOfWeek == selectedDayOfWeek }
            .sortedBy { parseLocalTimeOrNull(it.startTime) ?: LocalTime.MIN }

        // Paso 1 (Override)
        val activeOverride = overrides
            .filter { it.isActive }
            .firstOrNull { overrideDto ->
                val expiresInstant = parseIsoToInstant(overrideDto.expiresAt)
                expiresInstant != null && expiresInstant.isAfter(nowInLima.toInstant())
            }

        val todayActualSchedules = schedules
            .filter { it.dayOfWeek == todayLimaDay }
            .sortedBy { parseLocalTimeOrNull(it.startTime) ?: LocalTime.MIN }

        val currentSchedule = todayActualSchedules.firstOrNull { schedule ->
            val start = parseLocalTimeOrNull(schedule.startTime)
            val end = parseLocalTimeOrNull(schedule.endTime)
            if (start != null && end != null) {
                !currentTime.isBefore(start) && currentTime.isBefore(end)
            } else false
        }

        if (activeOverride != null) {
            val concurrentVirtualClass = if (currentSchedule != null && (
                    currentSchedule.modality.equals("virtual", ignoreCase = true) ||
                    currentSchedule.location.contains("virtual", ignoreCase = true) ||
                    currentSchedule.category.equals("virtual", ignoreCase = true)
                )) {
                currentSchedule
            } else null

            return UserCurrentStatus.Override(
                displayName = displayName,
                customLocation = activeOverride.customLocation,
                customMessage = activeOverride.customMessage,
                expiresAt = activeOverride.expiresAt,
                concurrentVirtualClass = concurrentVirtualClass,
                selectedDayOfWeek = selectedDayOfWeek,
                todaySchedules = daySchedules
            )
        }

        // Paso 2 (Schedule UPAO para el día actual)
        if (currentSchedule != null) {
            val start = parseLocalTimeOrNull(currentSchedule.startTime) ?: currentTime
            val end = parseLocalTimeOrNull(currentSchedule.endTime) ?: currentTime
            val totalSec = Duration.between(start, end).seconds.coerceAtLeast(1)
            val elapsedSec = Duration.between(start, currentTime).seconds.coerceAtLeast(0)
            val remainingMin = (Duration.between(currentTime, end).seconds / 60).coerceAtLeast(0)
            val progress = (elapsedSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)

            return UserCurrentStatus.InClass(
                displayName = displayName,
                currentSchedule = currentSchedule,
                remainingMinutes = remainingMin,
                progress = progress,
                selectedDayOfWeek = selectedDayOfWeek,
                todaySchedules = daySchedules
            )
        }

        // Paso 3 (Fallback: Tiempo libre / En casa)
        return UserCurrentStatus.FreeTime(
            displayName = displayName,
            selectedDayOfWeek = selectedDayOfWeek,
            todaySchedules = daySchedules
        )
    }

    fun parseLocalTimeOrNull(timeStr: String): LocalTime? {
        return try {
            val trimmed = timeStr.trim()
            val parts = trimmed.split(":")
            if (parts.size >= 2) {
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                val second = if (parts.size >= 3) parts[2].toInt() else 0
                LocalTime.of(hour, minute, second)
            } else {
                LocalTime.parse(trimmed)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseIsoToInstant(isoString: String): Instant? {
        return try {
            Instant.parse(isoString)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).toInstant()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(isoString).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
