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
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.sessions.FirebaseSessions.Companion.TAG
import com.google.firebase.sessions.TimeProvider
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
  @Background private val backgroundDispatcher: CoroutineContext,
  private val timeProvider: TimeProvider,
  private val sessionConfigsDataStore: DataStore<SessionConfigs>,
) : SettingsCache {
  private val sessionConfigsAtomicReference = AtomicReference<SessionConfigs>()

  private val sessionConfigs: SessionConfigs
    get() {
      // Ensure configs are loaded from disk before the first access
      if (sessionConfigsAtomicReference.get() == null) {
        // Double check to avoid the `runBlocking` unless necessary
        sessionConfigsAtomicReference.compareAndSet(
          null,
          runBlocking { sessionConfigsDataStore.data.first() },
        )
      }

      return sessionConfigsAtomicReference.get()
    }

  init {
    CoroutineScope(backgroundDispatcher).launch {
      sessionConfigsDataStore.data.collect(sessionConfigsAtomicReference::set)
    }
  }

  override fun hasCacheExpired(): Boolean {
    val cacheUpdatedTimeSeconds = sessionConfigs.cacheUpdatedTimeSeconds
    val cacheDurationSeconds = sessionConfigs.cacheDurationSeconds

    if (cacheUpdatedTimeSeconds != null && cacheDurationSeconds != null) {
      val timeDifferenceSeconds = timeProvider.currentTime().seconds - cacheUpdatedTimeSeconds
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
    } catch (ex: IOException) {
      Log.w(TAG, "Failed to update config values: $ex")
    }
  }

  @VisibleForTesting
  internal suspend fun removeConfigs() =
    try {
      sessionConfigsDataStore.updateData { SessionConfigsSerializer.defaultValue }
    } catch (ex: IOException) {
      Log.w(TAG, "Failed to remove config values: $ex")
    }
}
