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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

internal data class SessionConfigs(
  val sessionEnabled: Boolean?,
  val sessionSamplingRate: Double?,
  val cacheDuration: Int?,
  val sessionRestartTimeout: Int?,
  val cacheUpdatedTime: Long?
)

internal class SettingsCache(private val store: DataStore<Preferences>) {
  private var sessionConfigs =
    SessionConfigs(
      sessionEnabled = null,
      sessionSamplingRate = null,
      sessionRestartTimeout = null,
      cacheDuration = null,
      cacheUpdatedTime = null
    )

  private object SettingsCacheKeys {
    val SETTINGS_CACHE_SESSIONS_ENABLED = booleanPreferencesKey("firebase_sessions_enabled")
    val SETTINGS_CACHE_SAMPLING_RATE = doublePreferencesKey("firebase_sessions_sampling_rate")
    val SETTINGS_CACHE_SESSIONS_RESTART_TIMEOUT_SECONDS =
      intPreferencesKey("firebase_sessions_restart_timeout")
    val SETTINGS_CACHE_SESSIONS_CACHE_DURATION_SECONDS =
      intPreferencesKey("firebase_sessions_cache_duration")
    val SETTINGS_CACHE_SESSIONS_CACHE_UPDATED_TIME =
      longPreferencesKey("firebase_sessions_cache_updated_time")
  }

  private suspend fun updateSessionConfigs() = mapSessionConfigs(store.data.first().toPreferences())

  private fun mapSessionConfigs(settings: Preferences): SessionConfigs {
    val sessionEnabled = settings[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_ENABLED]
    val sessionSamplingRate = settings[SettingsCacheKeys.SETTINGS_CACHE_SAMPLING_RATE]
    val sessionRestartTimeout =
      settings[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_RESTART_TIMEOUT_SECONDS]
    val cacheDuration = settings[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_CACHE_DURATION_SECONDS]
    val cacheUpdatedTime = settings[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_CACHE_UPDATED_TIME]

    sessionConfigs =
      SessionConfigs(
        sessionEnabled = sessionEnabled,
        sessionSamplingRate = sessionSamplingRate,
        sessionRestartTimeout = sessionRestartTimeout,
        cacheDuration = cacheDuration,
        cacheUpdatedTime = cacheUpdatedTime
      )
    return sessionConfigs
  }

  internal fun hasCacheExpired(): Boolean {
    if (sessionConfigs.cacheUpdatedTime != null) {
      val currentTimestamp = System.currentTimeMillis()
      val timeDifferenceSeconds = (currentTimestamp - sessionConfigs.cacheUpdatedTime!!) / 1000
      if (timeDifferenceSeconds < sessionConfigs.cacheDuration!!) return false
    }
    return true
  }

  fun sessionsEnabled(): Boolean? {
    return sessionConfigs.sessionEnabled
  }

  fun sessionSamplingRate(): Double? {
    return sessionConfigs.sessionSamplingRate
  }

  fun sessionRestartTimeout(): Int? {
    return sessionConfigs.sessionRestartTimeout
  }

  suspend fun updateSettingsEnabled(enabled: Boolean?) {
    store.edit { preferences ->
      enabled?.run { preferences[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_ENABLED] = enabled }
        ?: run { preferences.remove(SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_ENABLED) }
    }
    updateSessionConfigs()
  }

  suspend fun updateSamplingRate(rate: Double?) {
    store.edit { preferences ->
      rate?.run { preferences[SettingsCacheKeys.SETTINGS_CACHE_SAMPLING_RATE] = rate }
        ?: run { preferences.remove(SettingsCacheKeys.SETTINGS_CACHE_SAMPLING_RATE) }
    }
    updateSessionConfigs()
  }

  suspend fun updateSessionRestartTimeout(timeoutInSeconds: Int?) {
    store.edit { preferences ->
      timeoutInSeconds?.run {
        preferences[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_RESTART_TIMEOUT_SECONDS] =
          timeoutInSeconds
      }
        ?: run {
          preferences.remove(SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_RESTART_TIMEOUT_SECONDS)
        }
    }
    updateSessionConfigs()
  }

  suspend fun updateSessionCacheDuration(cacheDurationInSeconds: Int?) {
    store.edit { preferences ->
      cacheDurationInSeconds?.run {
        preferences[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_CACHE_DURATION_SECONDS] =
          cacheDurationInSeconds
      }
        ?: run {
          preferences.remove(SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_CACHE_DURATION_SECONDS)
        }
    }
    updateSessionConfigs()
  }

  suspend fun updateSessionCacheUpdatedTime(cacheUpdatedTime: Long?) {
    store.edit { preferences ->
      cacheUpdatedTime?.run {
        preferences[SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_CACHE_UPDATED_TIME] = cacheUpdatedTime
      }
        ?: run { preferences.remove(SettingsCacheKeys.SETTINGS_CACHE_SESSIONS_CACHE_UPDATED_TIME) }
    }
    updateSessionConfigs()
  }

  suspend fun removeConfigs() {
    val configs = store
    store.edit { preferences ->
      preferences.clear()
      updateSessionConfigs()
    }
  }

  companion object {
    private const val TAG = "SessionSettings"
  }
}
