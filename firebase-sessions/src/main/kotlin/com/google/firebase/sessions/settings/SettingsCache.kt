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

package com.google.firebase.sessions.settings

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal data class SessionConfigs(
  val sessionEnabled: Boolean?,
  val sessionSamplingRate: Double?,
  val sessionRestartTimeout: Int?,
  val cacheDuration: Int?,
  val cacheUpdatedTime: Long?,
)

internal class SettingsCache(private val dataStore: DataStore<Preferences>) {
  private lateinit var sessionConfigs: SessionConfigs

  init {
    // Block until the cache is loaded from disk to ensure cache
    // values are valid and readable from the main thread on init.
    runBlocking { updateSessionConfigs(dataStore.data.first().toPreferences()) }
  }

  /** Update session configs from the given [preferences]. */
  private fun updateSessionConfigs(preferences: Preferences) {
    sessionConfigs =
      SessionConfigs(
        sessionEnabled = preferences[SESSIONS_ENABLED],
        sessionSamplingRate = preferences[SAMPLING_RATE],
        sessionRestartTimeout = preferences[RESTART_TIMEOUT_SECONDS],
        cacheDuration = preferences[CACHE_DURATION_SECONDS],
        cacheUpdatedTime = preferences[CACHE_UPDATED_TIME]
      )
  }

  internal fun hasCacheExpired(): Boolean {
    val cacheUpdatedTime = sessionConfigs.cacheUpdatedTime
    val cacheDuration = sessionConfigs.cacheDuration

    if (cacheUpdatedTime != null && cacheDuration != null) {
      val timeDifferenceSeconds = (System.currentTimeMillis() - cacheUpdatedTime) / 1000
      if (timeDifferenceSeconds < cacheDuration) {
        return false
      }
    }
    return true
  }

  fun sessionsEnabled(): Boolean? = sessionConfigs.sessionEnabled

  fun sessionSamplingRate(): Double? = sessionConfigs.sessionSamplingRate

  fun sessionRestartTimeout(): Int? = sessionConfigs.sessionRestartTimeout

  suspend fun updateSettingsEnabled(enabled: Boolean?) {
    updateConfigValue(SESSIONS_ENABLED, enabled)
  }

  suspend fun updateSamplingRate(rate: Double?) {
    updateConfigValue(SAMPLING_RATE, rate)
  }

  suspend fun updateSessionRestartTimeout(timeoutInSeconds: Int?) {
    updateConfigValue(RESTART_TIMEOUT_SECONDS, timeoutInSeconds)
  }

  suspend fun updateSessionCacheDuration(cacheDurationInSeconds: Int?) {
    updateConfigValue(CACHE_DURATION_SECONDS, cacheDurationInSeconds)
  }

  suspend fun updateSessionCacheUpdatedTime(cacheUpdatedTime: Long?) {
    updateConfigValue(CACHE_UPDATED_TIME, cacheUpdatedTime)
  }

  @VisibleForTesting
  internal suspend fun removeConfigs() {
    try {
      dataStore.edit { preferences ->
        preferences.clear()
        updateSessionConfigs(preferences)
      }
    } catch (e: IOException) {
      Log.w(
        TAG,
        "Failed to remove config values: $e",
      )
    }
  }

  /** Updated the config value, or remove the key if the value is null. */
  private suspend fun <T> updateConfigValue(key: Preferences.Key<T>, value: T?) {
    // TODO(mrober): Refactor these to update all the values in one transaction.
    try {
      dataStore.edit { preferences ->
        if (value != null) {
          preferences[key] = value
        } else {
          preferences.remove(key)
        }
        updateSessionConfigs(preferences)
      }
    } catch (ex: IOException) {
      Log.w(TAG, "Failed to update cache config value: $ex")
    }
  }

  private companion object {
    const val TAG = "SettingsCache"

    val SESSIONS_ENABLED = booleanPreferencesKey("firebase_sessions_enabled")
    val SAMPLING_RATE = doublePreferencesKey("firebase_sessions_sampling_rate")
    val RESTART_TIMEOUT_SECONDS = intPreferencesKey("firebase_sessions_restart_timeout")
    val CACHE_DURATION_SECONDS = intPreferencesKey("firebase_sessions_cache_duration")
    val CACHE_UPDATED_TIME = longPreferencesKey("firebase_sessions_cache_updated_time")
  }
}
