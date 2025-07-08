/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.sessions.testing

import com.google.firebase.sessions.TimeProvider
import com.google.firebase.sessions.settings.SessionConfigs
import com.google.firebase.sessions.settings.SessionConfigsSerializer
import com.google.firebase.sessions.settings.SettingsCache

/** Fake implementation of [SettingsCache]. */
internal class FakeSettingsCache(
  private val timeProvider: TimeProvider = FakeTimeProvider(),
  private var sessionConfigs: SessionConfigs = SessionConfigsSerializer.defaultValue,
) : SettingsCache {
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
    this.sessionConfigs = sessionConfigs
  }
}
