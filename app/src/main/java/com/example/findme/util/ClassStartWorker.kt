package com.example.findme.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ClassStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Actividad iniciada"
        val message = inputData.getString("message") ?: "Es hora de tu actividad."
        
        NotificationHelper.showStatusNotification(applicationContext, title, message)
        
        return Result.success()
    }
}
