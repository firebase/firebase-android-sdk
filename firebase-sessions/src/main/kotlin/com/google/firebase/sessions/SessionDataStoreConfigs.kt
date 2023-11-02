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

package com.google.firebase.sessions

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Base64
import com.google.android.gms.common.util.ProcessUtils
import com.google.firebase.sessions.settings.SessionsSettings

/**
 * Util object for handling DataStore<Preferences> configs in multi-process apps safely.
 *
 * This can be removed when datastore-preferences:1.1.0 becomes stable.
 */
object SessionDataStoreConfigs {
  /** Sanitized process name to use in config filenames. */
  private val PROCESS_NAME =
    Base64.encodeToString(
      getProcessName().encodeToByteArray(),
      Base64.NO_WRAP or Base64.URL_SAFE, // URL safe is also filename safe.
    )

  /** Config name for [SessionDatastore] */
  val SESSIONS_CONFIGS_NAME = "firebase_session_${PROCESS_NAME}_data"

  /** Config name for [SessionsSettings] */
  val SETTINGS_CONFIGS_NAME = "firebase_session_${PROCESS_NAME}_settings"

  /** Returns the current process name or an empty string if it could not be found. */
  private fun getProcessName(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return Process.myProcessName()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Application.getProcessName()?.let {
        return it
      }
    }

    // GMS core has different ways to get the process name on old api levels.
    ProcessUtils.getMyProcessName()?.let {
      return it
    }

    // Default to an empty string if nothing works.
    return ""
  }
}
