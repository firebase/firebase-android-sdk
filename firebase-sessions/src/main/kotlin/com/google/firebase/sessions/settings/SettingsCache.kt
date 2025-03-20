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
import androidx.datastore.core.DataStore
import com.google.firebase.sessions.TimeProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal interface SettingsCache {
  fun hasCacheExpired(): Boolean

  fun sessionsEnabled(): Boolean?

  fun sessionSamplingRate(): Double?

  fun sessionRestartTimeout(): Int?

  suspend fun updateConfigs(sessionConfigs: SessionConfigs)
}

@Singleton
internal class SettingsCacheImpl
@Inject
constructor(
  private val timeProvider: TimeProvider,
  private val sessionConfigsDataStore: DataStore<SessionConfigs>,
) : SettingsCache {
  private var sessionConfigs: SessionConfigs

  init {
    // Block until the cache is loaded from disk to ensure cache
    // values are valid and readable from the main thread on init.
    runBlocking { sessionConfigs = sessionConfigsDataStore.data.first() }
  }

  override fun hasCacheExpired(): Boolean {
    val cacheUpdatedTimeMs = sessionConfigs.cacheUpdatedTimeMs
    val cacheDurationSeconds = sessionConfigs.cacheDurationSeconds

    if (cacheUpdatedTimeMs != null && cacheDurationSeconds != null) {
      val timeDifferenceSeconds = (timeProvider.currentTime().ms - cacheUpdatedTimeMs) / 1000
      if (timeDifferenceSeconds < cacheDurationSeconds) {
        return false
      }
    }
    return true
  }

  override fun sessionsEnabled(): Boolean? = sessionConfigs.sessionsEnabled

  override fun sessionSamplingRate(): Double? = sessionConfigs.sessionSamplingRate

  override fun sessionRestartTimeout(): Int? = sessionConfigs.sessionTimeoutSeconds

  override suspend fun updateConfigs(sessionConfigs: SessionConfigs) {
    try {
      sessionConfigsDataStore.updateData { sessionConfigs }
      this.sessionConfigs = sessionConfigs
    } catch (ex: IOException) {
      Log.w(TAG, "Failed to update config values: $ex")
    }
  }

  internal suspend fun removeConfigs() =
    try {
      sessionConfigsDataStore.updateData { SessionConfigsSerializer.defaultValue }
    } catch (ex: IOException) {
      Log.w(TAG, "Failed to remove config values: $ex")
    }

  private companion object {
    const val TAG = "SettingsCache"
  }
}
