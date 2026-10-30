package com.example.findme.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class AppAccent(val label: String) { Indigo("Índigo"), Mint("Menta"), Rose("Rosa") }
enum class AccountAlert { Activities, Statuses, Messages }
data class AccountSettings(
    val activities: Boolean = true,
    val statuses: Boolean = true,
    val messages: Boolean = true,
    val accent: AppAccent = AppAccent.Indigo
)

class AccountSettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore
    private val accentKey = stringPreferencesKey("app_accent")
    private fun alertKey(id: String, alert: AccountAlert) = booleanPreferencesKey("account_${id}_${alert.name}")
    private fun accent(value: String?) = AppAccent.entries.find { it.name == value } ?: AppAccent.Indigo
    val accent = store.data.map { accent(it[accentKey]) }.distinctUntilChanged()
    fun settings(id: String) = store.data.map { prefs -> AccountSettings(
        activities = prefs[alertKey(id, AccountAlert.Activities)] ?: true,
        statuses = prefs[alertKey(id, AccountAlert.Statuses)] ?: true,
        messages = prefs[alertKey(id, AccountAlert.Messages)] ?: true,
        accent = accent(prefs[accentKey])
    ) }.distinctUntilChanged()
    suspend fun setAlert(id: String, alert: AccountAlert, enabled: Boolean) {
        store.edit { it[alertKey(id, alert)] = enabled }
    }
    suspend fun setAccent(value: AppAccent) { store.edit { it[accentKey] = value.name } }
}
