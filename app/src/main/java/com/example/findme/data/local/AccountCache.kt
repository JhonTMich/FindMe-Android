package com.example.findme.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.findme.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The last successful sync of one account, shown while offline or before the first request returns. */
@Serializable
data class AccountSnapshot(
    val owner: String,
    val profile: AccountProfile,
    val profiles: List<AccountProfile>,
    val schedules: List<ScheduleDto>,
    val statuses: List<StatusOverrideDto>,
    val connections: List<PersonConnection>,
    val messages: List<QuickMessage>,
    val signals: List<ConnectionSignal>,
    val syncedAt: String
)

/** Kept on the device only (excluded from backups) and removed when the session ends. */
object AccountCache {
    private val key = stringPreferencesKey("account_cache_v1")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(value: String?, owner: String): AccountSnapshot? = value
        ?.let { runCatching { json.decodeFromString(AccountSnapshot.serializer(), it) }.getOrNull() }
        ?.takeIf { it.owner == owner && it.profile.id == owner }

    fun encode(snapshot: AccountSnapshot): String = json.encodeToString(AccountSnapshot.serializer(), snapshot)

    suspend fun save(context: Context, snapshot: AccountSnapshot) {
        context.dataStore.edit { it[key] = encode(snapshot) }
    }

    suspend fun load(context: Context, owner: String): AccountSnapshot? = decode(context.dataStore.data.first()[key], owner)

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.remove(key) }
    }
}
