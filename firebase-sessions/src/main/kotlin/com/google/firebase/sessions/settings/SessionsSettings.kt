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

import android.content.Context
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.ApplicationInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [SessionsSettings] manages all the configs that are relevant to the sessions library.
 *
 * @hide
 */
internal class SessionsSettings(
  val context: Context,
  val firebaseInstallationsApi: FirebaseInstallationsApi,
  val appInfo: ApplicationInfo
) {

  private var localOverrideSettings = LocalOverrideSettings(context)
  private var remoteSettings = RemoteSettings(context, firebaseInstallationsApi, appInfo)

  // Order of preference for all the configs below:
  // 1. Honor local overrides
  // 2. If no local overrides, use remote config
  // 3. If no remote config, fall back to SDK defaults.

  // Setting to qualify if sessions service is enabled.
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

  // Setting that provides the sessions sampling rate.
  val samplingRate: Double
    get() {
      localOverrideSettings.samplingRate?.let {
        return it
      }
      remoteSettings.samplingRate?.let {
        return it
      }
      // SDK Default
      return 1.0
    }

  // Background timeout config value before which a new session is generated
  val sessionRestartTimeout: Duration
    get() {
      localOverrideSettings.sessionRestartTimeout?.let {
        return it
      }
      remoteSettings.sessionRestartTimeout?.let {
        return it
      }
      // SDK Default
      return 30.minutes
    }

  // Update the settings for all the settings providers
  fun updateSettings() {
    // Placeholder to initiate settings update on different sources
    localOverrideSettings.updateSettings()
    remoteSettings.updateSettings()
  }
}
