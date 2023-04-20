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
import android.content.pm.PackageManager
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class LocalOverrideSettings(val context: Context) : SettingsProvider {

  private val sessions_metadata_flag_sessionsEnabled = "firebase_sessions_enabled"
  private val sessions_metadata_flag_sessionRestartTimeout =
    "firebase_sessions_sessions_restart_timeout"
  private val sessions_metadata_flag_samplingRate = "firebase_sessions_sampling_rate"
  private val metadata =
    context.packageManager
      .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
      .metaData

  override val sessionEnabled: Boolean?
    get() {
      metadata?.let {
        if (it.containsKey(sessions_metadata_flag_sessionsEnabled)) {
          return it.getBoolean(sessions_metadata_flag_sessionsEnabled)
        }
      }
      return null
    }

  override val sessionRestartTimeout: Duration?
    get() {
      metadata?.let {
        if (it.containsKey(sessions_metadata_flag_sessionRestartTimeout)) {
          val timeoutInSeconds = it.getInt(sessions_metadata_flag_sessionRestartTimeout)
          val duration = timeoutInSeconds.toDuration(DurationUnit.SECONDS)
          return duration
        }
      }
      return null
    }

  override val samplingRate: Double?
    get() {
      metadata?.let {
        if (it.containsKey(sessions_metadata_flag_samplingRate)) {
          return it.getDouble(sessions_metadata_flag_samplingRate)
        }
      }
      return null
    }

  override fun updateSettings() {
    // Nothing to be done here since there is nothing to be updated.
  }

  override fun isSettingsStale(): Boolean {
    // Settings are never stale since all of these are from Manifest file.
    return false
  }
}
