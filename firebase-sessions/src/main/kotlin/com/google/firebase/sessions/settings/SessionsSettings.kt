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

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.sessions.FirebaseSessionsComponent
import com.google.firebase.sessions.LocalOverrideSettingsProvider
import com.google.firebase.sessions.RemoteSettingsProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** [SessionsSettings] manages all the configs that are relevant to the sessions library. */
@Singleton
internal class SessionsSettings
@Inject
constructor(
  @LocalOverrideSettingsProvider private val localOverrideSettings: SettingsProvider,
  @RemoteSettingsProvider private val remoteSettings: SettingsProvider,
) {

  // Order of preference for all the configs below:
  // 1. Honor local overrides
  // 2. If no local overrides, use remote config
  // 3. If no remote config, fall back to SDK defaults.

  /** Setting to qualify if sessions service is enabled. */
  val sessionsEnabled: Boolean
    get() {
      localOverrideSettings.sessionEnabled?.let {
        return it
      }
      remoteSettings.sessionEnabled?.let {
        return it
      }
      // SDK Default
      return true
    }

  /** Setting that provides the sessions sampling rate. */
  val samplingRate: Double
    get() {
      localOverrideSettings.samplingRate?.let {
        if (isValidSamplingRate(it)) {
          return it
        }
      }
      remoteSettings.samplingRate?.let {
        if (isValidSamplingRate(it)) {
          return it
        }
      }
      // SDK Default
      return 1.0
    }

  /** Background timeout config value before which a new session is generated. */
  val sessionRestartTimeout: Duration
    get() {
      localOverrideSettings.sessionRestartTimeout?.let {
        if (isValidSessionRestartTimeout(it)) {
          return it
        }
      }
      remoteSettings.sessionRestartTimeout?.let {
        if (isValidSessionRestartTimeout(it)) {
          return it
        }
      }
      // SDK Default
      return 30.minutes
    }

  private fun isValidSamplingRate(samplingRate: Double): Boolean = samplingRate in 0.0..1.0

  private fun isValidSessionRestartTimeout(sessionRestartTimeout: Duration): Boolean {
    return sessionRestartTimeout.isPositive() && sessionRestartTimeout.isFinite()
  }

  /** Update the settings for all the settings providers. */
  suspend fun updateSettings() {
    localOverrideSettings.updateSettings()
    remoteSettings.updateSettings()
  }

  internal companion object {
    val instance: SessionsSettings
      get() = Firebase.app[FirebaseSessionsComponent::class.java].sessionsSettings
  }
}
