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
    val familyCode: String,
    val motherName: String = "Mamá"
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_FAMILY_CODE = stringPreferencesKey("family_code")
        private val KEY_MOTHER_NAME = stringPreferencesKey("mother_name")
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

    suspend fun getUserSession(): UserSession? {
        return userSessionFlow.firstOrNull()
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

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
