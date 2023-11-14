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

import android.util.Base64
import com.google.firebase.sessions.settings.SessionsSettings

/**
 * Util object for handling DataStore<Preferences> configs in multi-process apps safely.
 *
 * This can be removed when datastore-preferences:1.1.0 becomes stable.
 */
internal object SessionDataStoreConfigs {
  /** Sanitized process name to use in config filenames. */
  private val PROCESS_NAME =
    Base64.encodeToString(
      ProcessDetailsProvider.getProcessName().encodeToByteArray(),
      Base64.NO_WRAP or Base64.URL_SAFE, // URL safe is also filename safe.
    )

  /** Config name for [SessionDatastore] */
  val SESSIONS_CONFIG_NAME = "firebase_session_${PROCESS_NAME}_data"

  /** Config name for [SessionsSettings] */
  val SETTINGS_CONFIG_NAME = "firebase_session_${PROCESS_NAME}_settings"
}
