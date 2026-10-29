package com.example.findme.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val SYSTEM_ZONE: ZoneId get() = ZoneId.systemDefault()

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

    suspend fun getAdminProfile(): Result<ProfileDto> {
        return try {
            val profiles = SupabaseClientProvider.client.postgrest["profiles"]
                .select()
                .decodeList<ProfileDto>()

            val found = profiles.firstOrNull()
            if (found != null) {
                Result.success(found)
            } else {
                Result.failure(Exception("No se encontró perfil de administrador"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión al obtener perfil admin: ${e.message}"))
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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

    suspend fun setOnTheStreetOverride(userId: String): Result<Unit> {
        return try {
            clearOverrides(userId)
            // Expiración configurable, usaremos 2 horas por defecto
            val expiresAtIso = Instant.now().plusSeconds(2 * 3600).toString()
            val override = StatusOverrideDto(
                userId = userId,
                isActive = true,
                customLocation = "En la calle",
                customMessage = "Fuera de casa",
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
    ): Flow<UserCurrentStatus> = observeStatusWithClock(
        displayName = displayName,
        schedules = flowOf(schedules),
        overrides = observeRemoteValues(userId, "overrides") { fetchOverrides(userId) },
        selectedDayOfWeek = selectedDayOfWeek
    )

    fun observeCurrentStatus(
        userId: String,
        displayName: String,
        selectedDayOfWeek: Int
    ): Flow<UserCurrentStatus> = observeStatusWithClock(
        displayName = displayName,
        schedules = observeRemoteValues(userId, "schedules") { fetchSchedules(userId) },
        overrides = observeRemoteValues(userId, "overrides") { fetchOverrides(userId) },
        selectedDayOfWeek = selectedDayOfWeek
    )

    // The clock runs independently of database traffic and WebSocket subscriptions.
    internal fun observeStatusWithClock(
        displayName: String,
        schedules: Flow<List<ScheduleDto>>,
        overrides: Flow<List<StatusOverrideDto>>,
        selectedDayOfWeek: Int,
        ticks: Flow<ZonedDateTime> = systemClockTicks()
    ): Flow<UserCurrentStatus> = combine(schedules, overrides, ticks) { currentSchedules, currentOverrides, now ->
        calculateStatus(displayName, currentSchedules, currentOverrides, selectedDayOfWeek, now)
    }.distinctUntilChanged()

    private fun systemClockTicks(): Flow<ZonedDateTime> = flow {
        while (true) {
            emit(ZonedDateTime.now(SYSTEM_ZONE))
            delay(15_000)
        }
    }

    private fun <T> observeRemoteValues(
        userId: String,
        source: String,
        fetch: suspend () -> T
    ): Flow<T> = channelFlow {
        val refreshMutex = Mutex()
        suspend fun refresh() = refreshMutex.withLock { send(fetch()) }
        refresh()
        // Polling remains available even when a subscribed socket is silent or reconnecting.
        launch {
            while (isActive) {
                delay(15_000)
                refresh()
            }
        }
        launch {
            try {
                val client = SupabaseClientProvider.client
                val channel = client.realtime.channel("status-$source-$userId-${java.util.UUID.randomUUID()}") {}
                try {
                    coroutineScope {
                        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public")
                        launch { changes.collect { refresh() } }
                        channel.subscribe()
                        awaitCancellation()
                    }
                } finally {
                    withContext(NonCancellable) {
                        withTimeoutOrNull(2_000) { client.realtime.removeChannel(channel) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("StatusRepository", "Realtime unavailable; continuing to poll", error)
            }
        }
    }

    suspend fun getDirectCurrentStatus(
        userId: String,
        displayName: String,
        selectedDayOfWeek: Int = ZonedDateTime.now(SYSTEM_ZONE).dayOfWeek.value,
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
        nowInLima: ZonedDateTime = ZonedDateTime.now(SYSTEM_ZONE)
    ): UserCurrentStatus {
        val todayLimaDay = nowInLima.dayOfWeek.value
        val currentTime = nowInLima.toLocalTime()

        val daySchedules = schedules
            .filter { it.dayOfWeek == selectedDayOfWeek }
            .sortedBy { parseLocalTimeOrNull(it.startTime) ?: LocalTime.MIN }

        // Find an unexpired manual announcement without overriding the schedule.
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

        // An activity is active from its start time (inclusive) until its end (exclusive).
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

        // Manual announcements apply when there is no activity in progress.
        if (activeOverride != null) {
            return UserCurrentStatus.Override(
                displayName = displayName,
                customLocation = activeOverride.customLocation,
                customMessage = activeOverride.customMessage,
                expiresAt = activeOverride.expiresAt,
                selectedDayOfWeek = selectedDayOfWeek,
                todaySchedules = daySchedules
            )
        }

        // No scheduled activity or active announcement.
        return UserCurrentStatus.FreeTime(
            displayName = displayName,
            selectedDayOfWeek = selectedDayOfWeek,
            todaySchedules = daySchedules
        )
    }

    fun parseLocalTimeOrNull(timeStr: String): LocalTime? {
        val normalized = timeStr.trim().let { value ->
            // Preserve support for a single-digit hour, while accepting PostgreSQL fractional seconds.
            if (value.indexOf(':') == 1) "0$value" else value
        }
        return try {
            LocalTime.parse(normalized)
        } catch (_: java.time.format.DateTimeParseException) {
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
