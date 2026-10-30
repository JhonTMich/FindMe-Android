package com.example.findme.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserSession(
    val userId: String,
    val displayName: String,
    val familyCode: String,
    val motherName: String = "Mamá"
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_FAMILY_CODE = stringPreferencesKey("family_code")
        private val KEY_MOTHER_NAME = stringPreferencesKey("mother_name")
        private val KEY_ACTIVITY_ALERTS = booleanPreferencesKey("activity_alerts")
        private val KEY_STATUS_ALERTS = booleanPreferencesKey("status_alerts")
        private val KEY_IS_ADMIN_ROLE = booleanPreferencesKey("is_admin_role")

        const val ADMIN_PASSWORD = "1234"
    }

    val userSessionFlow: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val userId = prefs[KEY_USER_ID]
        val displayName = prefs[KEY_DISPLAY_NAME]
        val familyCode = prefs[KEY_FAMILY_CODE]
        val motherName = prefs[KEY_MOTHER_NAME]

        if (!userId.isNullOrEmpty() && !displayName.isNullOrEmpty()) {
            UserSession(
                userId = userId,
                displayName = displayName,
                familyCode = familyCode.orEmpty(),
                motherName = if (!motherName.isNullOrEmpty()) motherName else "Mamá"
            )
        } else {
            null
        }
    }

    val isAdminRoleFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_ADMIN_ROLE] ?: false
    }

    val activityAlerts = context.dataStore.data.map { it[KEY_ACTIVITY_ALERTS] ?: true }
    val statusAlerts = context.dataStore.data.map { it[KEY_STATUS_ALERTS] ?: true }
    suspend fun setActivityAlerts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ACTIVITY_ALERTS] = enabled }
    }
    suspend fun setStatusAlerts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STATUS_ALERTS] = enabled }
    }

    suspend fun getUserSession(): UserSession? {
        return userSessionFlow.firstOrNull()
    }

    suspend fun isAdminRole(): Boolean {
        return isAdminRoleFlow.firstOrNull() ?: false
    }

    suspend fun saveUserSession(
        userId: String,
        displayName: String,
        familyCode: String,
        motherName: String = "Mamá"
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_FAMILY_CODE] = familyCode
            prefs[KEY_MOTHER_NAME] = motherName
        }
    }

    suspend fun setAdminRole(isAdmin: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_ADMIN_ROLE] = isAdmin
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_FAMILY_CODE)
            prefs.remove(KEY_MOTHER_NAME)
            prefs.remove(KEY_IS_ADMIN_ROLE)
        }
    }
}
