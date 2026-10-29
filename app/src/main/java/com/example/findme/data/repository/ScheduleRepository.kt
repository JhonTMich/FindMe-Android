package com.example.findme.data.repository

import android.util.Log
import com.example.findme.data.local.ScheduleDao
import com.example.findme.data.local.ScheduleEntity
import com.example.findme.data.local.toEntity
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ScheduleRepository(private val scheduleDao: ScheduleDao) {

    fun getWeeklyScheduleStream(userId: String): Flow<List<ScheduleEntity>> =
        scheduleDao.getAllSchedulesFlow(userId)

    fun getSchedulesByDayStream(userId: String, dayOfWeek: Int): Flow<List<ScheduleEntity>> =
        scheduleDao.getSchedulesByDayFlow(userId, dayOfWeek)

    suspend fun syncScheduleFromNetwork(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteSchedules = SupabaseClientProvider.client.postgrest["schedule"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<ScheduleDto>()

            scheduleDao.replaceUserSchedules(userId, remoteSchedules.map { it.toEntity(userId) })
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("ScheduleRepository", "Error syncing schedule from network: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun clearUserSchedule(userId: String) = withContext(Dispatchers.IO) {
        try {
            scheduleDao.clearUserSchedule(userId)
        } catch (e: Exception) {
            Log.e("ScheduleRepository", "Error clearing user schedule: ${e.message}", e)
        }
    }
}
