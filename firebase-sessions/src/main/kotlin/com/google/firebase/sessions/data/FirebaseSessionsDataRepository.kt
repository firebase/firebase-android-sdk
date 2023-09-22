package com.google.firebase.sessions.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class FirebaseSessionsData(
    val sessionId: String?
)

/**
 * Persists session data that needs to be synchronized across processes
 */
class FirebaseSessionsDataRepository(private val context: Context) {
    private val TAG = "FirebaseSessionsRepo"

    private object FirebaseSessionDataKeys {
        val SESSION_ID = stringPreferencesKey("session_id")
    }

    val firebaseSessionDataFlow: Flow<FirebaseSessionsData> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading stored session data.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            mapSessionsData(preferences)
        }

    suspend fun updateSessionId(sessionId: String) {
        context.dataStore.edit { preferences ->
            preferences[FirebaseSessionDataKeys.SESSION_ID] = sessionId
        }
    }

    private fun mapSessionsData(preferences: Preferences): FirebaseSessionsData =
        FirebaseSessionsData(preferences[FirebaseSessionDataKeys.SESSION_ID])
}

const val SESSION_CONFIGS_NAME = "firebase_session_settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SESSION_CONFIGS_NAME)
