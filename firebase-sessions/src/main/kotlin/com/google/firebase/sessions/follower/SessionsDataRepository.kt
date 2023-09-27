/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sessions.follower

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
import kotlinx.coroutines.flow.map

internal data class FirebaseSessionsData(val sessionId: String?)

/** Persists session data that needs to be synchronized across processes */
internal class SessionsDataRepository(private val context: Context) {
  private val tag = "FirebaseSessionsRepo"

  private object FirebaseSessionDataKeys {
    val SESSION_ID = stringPreferencesKey("session_id")
  }

  val firebaseSessionDataFlow: Flow<FirebaseSessionsData> =
    context.dataStore.data
      .catch { exception ->
        Log.e(tag, "Error reading stored session data.", exception)
        emit(emptyPreferences())
      }
      .map { preferences -> mapSessionsData(preferences) }

  suspend fun updateSessionId(sessionId: String) {
    context.dataStore.edit { preferences ->
      preferences[FirebaseSessionDataKeys.SESSION_ID] = sessionId
    }
  }

  private fun mapSessionsData(preferences: Preferences): FirebaseSessionsData =
    FirebaseSessionsData(preferences[FirebaseSessionDataKeys.SESSION_ID])
}

private val Context.dataStore: DataStore<Preferences> by
  preferencesDataStore(name = "firebase_session_settings")
