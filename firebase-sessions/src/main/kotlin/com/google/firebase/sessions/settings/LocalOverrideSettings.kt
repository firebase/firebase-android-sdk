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
import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Singleton
internal class LocalOverrideSettings @Inject constructor(appContext: Context) : SettingsProvider {
  private val metadata =
    appContext.packageManager
      .getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
      .metaData
      ?: Bundle.EMPTY // Default to an empty bundle

  override val sessionEnabled: Boolean?
    get() =
      if (metadata.containsKey(SESSIONS_ENABLED)) {
        metadata.getBoolean(SESSIONS_ENABLED)
      } else {
        null
      }

  override val sessionRestartTimeout: Duration?
    get() =
      if (metadata.containsKey(SESSION_RESTART_TIMEOUT)) {
        val timeoutInSeconds = metadata.getInt(SESSION_RESTART_TIMEOUT)
        timeoutInSeconds.toDuration(DurationUnit.SECONDS)
      } else {
        null
      }

  override val samplingRate: Double?
    get() =
      if (metadata.containsKey(SAMPLING_RATE)) {
        metadata.getDouble(SAMPLING_RATE)
      } else {
        null
      }

  private companion object {
    const val SESSIONS_ENABLED = "firebase_sessions_enabled"
    const val SESSION_RESTART_TIMEOUT = "firebase_sessions_sessions_restart_timeout"
    const val SAMPLING_RATE = "firebase_sessions_sampling_rate"
  }
}
