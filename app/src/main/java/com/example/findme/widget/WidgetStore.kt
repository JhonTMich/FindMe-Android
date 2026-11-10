package com.example.findme.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.updateAll
import com.example.findme.data.local.dataStore
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.AccountRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WidgetData(
    val owner: String,
    val profiles: List<AccountProfile>,
    val schedules: List<ScheduleDto>,
    val statuses: List<StatusOverrideDto>,
    val syncedAt: Long = System.currentTimeMillis()
)

object WidgetStore {
    val cacheKey = stringPreferencesKey("status_widget_cache")
    val errorKey = stringPreferencesKey("status_widget_sync_error")
    fun selectionKey(id: Int) = stringPreferencesKey("status_widget_person_$id")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(value: String?): WidgetData? = value?.let { runCatching { json.decodeFromString<WidgetData>(it) }.getOrNull() }

    suspend fun publish(context: Context, owner: String, profiles: List<AccountProfile>, schedules: List<ScheduleDto>, statuses: List<StatusOverrideDto>, requestedAt: Long = Long.MAX_VALUE, canPublish: () -> Boolean = { true }) {
        context.dataStore.edit {
            if (!canPublish()) return@edit
            val cached = decode(it[cacheKey])
            if (cached?.owner == owner && cached.syncedAt > requestedAt) return@edit
            it[cacheKey] = json.encodeToString(WidgetData.serializer(), WidgetData(owner, profiles, schedules, statuses))
            it.remove(errorKey)
        }
        ConnectionWidget().updateAll(context)
        GlanceWidgetWorker.enqueueBoundaryUpdate(context)
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.remove(cacheKey); it.remove(errorKey) }
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("WidgetStatusBoundary")
        ConnectionWidget().updateAll(context)
    }

    suspend fun refresh(context: Context) = coroutineScope {
        val requestedAt = System.currentTimeMillis()
        val repo = AccountRepository()
        repo.auth.awaitInitialization()
        val owner = repo.auth.currentUserOrNull()?.id
        if (owner == null) { clear(context); return@coroutineScope }
        val profiles = async { repo.profiles() }
        val schedules = async { repo.accessibleSchedules() }
        val statuses = async { repo.accessibleStatuses() }
        val people = profiles.await()
        val activities = schedules.await()
        val overrides = statuses.await()
        // A request started before logout must never restore the previous account's data.
        if (repo.auth.currentUserOrNull()?.id == owner) publish(context, owner, people, activities, overrides, requestedAt) { repo.auth.currentUserOrNull()?.id == owner }
    }

    suspend fun select(context: Context, id: Int, owner: String, person: String) {
        context.dataStore.edit { it[selectionKey(id)] = "$owner|$person" }
        ConnectionWidget().updateAll(context)
    }

    suspend fun data(context: Context) = decode(context.dataStore.data.first()[cacheKey])
}
