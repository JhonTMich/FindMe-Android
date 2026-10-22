package com.example.findme.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val familyCode: String
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_FAMILY_CODE = stringPreferencesKey("family_code")
    }

    val userSessionFlow: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val userId = prefs[KEY_USER_ID]
        val displayName = prefs[KEY_DISPLAY_NAME]
        val familyCode = prefs[KEY_FAMILY_CODE]

        if (!userId.isNullOrEmpty() && !displayName.isNullOrEmpty()) {
            UserSession(
                userId = userId,
                displayName = displayName,
                familyCode = familyCode.orEmpty()
            )
        } else {
            null
        }
    }

    suspend fun getUserSession(): UserSession? {
        return userSessionFlow.firstOrNull()
    }

    suspend fun saveUserSession(userId: String, displayName: String, familyCode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_FAMILY_CODE] = familyCode
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
