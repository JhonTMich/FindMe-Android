package com.example.findme.util

import android.content.Context
import kotlinx.coroutines.flow.first
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ClassStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferences = com.example.findme.data.local.UserPreferencesRepository(applicationContext)
        if (!preferences.activityAlerts.first() || preferences.isAdminRole() || preferences.getUserSession() == null) {
            return Result.success()
        }
        val title = inputData.getString("title") ?: "Actividad iniciada"
        val message = inputData.getString("message") ?: "Es hora de tu actividad."
        
        NotificationHelper.showStatusNotification(applicationContext, title, message)
        
        return Result.success()
    }
}
