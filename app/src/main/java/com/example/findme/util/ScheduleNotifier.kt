package com.example.findme.util

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.repository.StatusRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ScheduleNotifier {
    fun scheduleAlertsForSchedules(context: Context, schedules: List<ScheduleDto>) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("class_alert")
        
        val now = ZonedDateTime.now(StatusRepository.LIMA_ZONE)
        
        schedules.forEach { schedule ->
            val startTime = StatusRepository.parseLocalTimeOrNull(schedule.startTime) ?: return@forEach
            
            try {
                // Calculamos para el día de hoy o el siguiente día que caiga ese dayOfWeek
                var scheduleTime = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(
                    java.time.DayOfWeek.of(schedule.dayOfWeek)
                )).withHour(startTime.hour).withMinute(startTime.minute).withSecond(0)
                
                if (scheduleTime.isBefore(now)) {
                    scheduleTime = scheduleTime.plusWeeks(1)
                }
                
                val delay = Duration.between(now, scheduleTime).toMillis()
                
                val data = Data.Builder()
                    .putString("title", "Inicio de Actividad")
                    .putString("message", "Inicia: ${schedule.title} (${schedule.location})")
                    .build()
                    
                val request = OneTimeWorkRequestBuilder<ClassStartWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag("class_alert")
                    .build()
                    
                workManager.enqueue(request)
            } catch (e: Exception) {
                // Capturar DateTimeException u otros para evitar crash
                e.printStackTrace()
            }
        }
    }
}
