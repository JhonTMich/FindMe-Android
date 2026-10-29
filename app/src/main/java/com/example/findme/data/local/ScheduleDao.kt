package com.example.findme.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules WHERE userId = :userId ORDER BY startTime ASC")
    fun getAllSchedulesFlow(userId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE userId = :userId AND dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    fun getSchedulesByDayFlow(userId: String, dayOfWeek: Int): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSchedules(schedules: List<ScheduleEntity>)

    @Transaction
    fun replaceUserSchedules(userId: String, schedules: List<ScheduleEntity>) {
        clearUserSchedule(userId)
        insertSchedules(schedules)
    }

    @Query("DELETE FROM schedules WHERE userId = :userId")
    fun clearUserSchedule(userId: String)
}
