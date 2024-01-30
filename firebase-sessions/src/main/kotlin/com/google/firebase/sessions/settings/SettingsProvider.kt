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

import kotlin.time.Duration

internal interface SettingsProvider {
  /** Setting to control if session collection is enabled. */
  val sessionEnabled: Boolean?

  /** Setting to represent when to restart a new session after app backgrounding. */
  val sessionRestartTimeout: Duration?

  /** Setting denoting the percentage of the sessions data that should be collected. */
  val samplingRate: Double?

  /** Function to initiate refresh of the settings for the provider. */
  suspend fun updateSettings() = Unit // Default to no op.

  /** Function representing if the settings are stale. */
  fun isSettingsStale(): Boolean = false // Default to false.
}
