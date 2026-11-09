package com.example.findme.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.example.findme.data.local.dataStore
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.activeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class GlanceWidgetWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Recalculate cached schedules even when the network is unavailable.
        context.dataStore.edit { it[longPreferencesKey("widget_clock")] = System.currentTimeMillis() }
        ConnectionWidget().updateAll(context)
        val result = try {
            withTimeout(20_000) { WidgetStore.refresh(context) }
            Result.success()
        } catch (_: TimeoutCancellationException) {
            markStale()
            Result.retry()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            markStale()
            Result.retry()
        }
        enqueueBoundaryUpdate(context)
        return result
    }

    private suspend fun markStale() {
        context.dataStore.edit { it[WidgetStore.errorKey] = "offline" }
        context.dataStore.edit { it[longPreferencesKey("widget_clock")] = System.currentTimeMillis() }
        ConnectionWidget().updateAll(context)
    }

    companion object {
        private const val WORK_NAME = "GlanceWidgetUpdateWorker"
        fun enqueuePeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<GlanceWidgetWorker>(15, TimeUnit.MINUTES).build())
        }
        fun enqueueRefresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("WidgetManualRefresh", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GlanceWidgetWorker>().build())
        }
        suspend fun enqueueBoundaryUpdate(context: Context, fromBoundary: Boolean = false) {
            val work = WorkManager.getInstance(context)
            if (androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIds(ConnectionWidget::class.java).isEmpty()) return
            val data = WidgetStore.data(context) ?: return
            val instant = Instant.now()
            val boundaries = data.profiles.flatMap { person ->
                val now = ZonedDateTime.now(runCatching { ZoneId.of(person.timezone) }.getOrDefault(ZoneId.systemDefault()))
                data.schedules.filter { it.userId == person.id }.flatMap { row ->
                    val start = StatusRepository.parseLocalTimeOrNull(row.startTime)?.let { time ->
                        var date = now.toLocalDate().plusDays(((row.dayOfWeek - now.dayOfWeek.value + 7) % 7).toLong()).atTime(time).atZone(now.zone)
                        if (!date.isAfter(now)) date = date.plusWeeks(1)
                        date.toInstant()
                    }
                    listOfNotNull(start, row.activeWindow(now)?.end?.toInstant())
                }
            } + data.statuses.filter { it.isActive }.mapNotNull { StatusRepository.parseIsoToInstant(it.expiresAt) }
            val next = boundaries.filter { it.isAfter(instant) }.minOrNull()
            if (next == null) { work.cancelUniqueWork("WidgetStatusBoundary"); return }
            WorkManager.getInstance(context).enqueueUniqueWork("WidgetStatusBoundary", if (fromBoundary) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetBoundaryWorker>()
                    .setInitialDelay(Duration.between(instant, next).toMillis().coerceAtLeast(1000), TimeUnit.MILLISECONDS).build())
        }
    }
}

class WidgetBoundaryWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        context.dataStore.edit { it[longPreferencesKey("widget_clock")] = System.currentTimeMillis() }
        ConnectionWidget().updateAll(context)
        GlanceWidgetWorker.enqueueBoundaryUpdate(context, fromBoundary = true)
        return Result.success()
    }
}
