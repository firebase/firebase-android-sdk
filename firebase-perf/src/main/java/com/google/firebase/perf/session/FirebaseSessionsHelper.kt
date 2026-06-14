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

package com.google.firebase.perf.session

import com.google.firebase.perf.util.Constants
import java.util.UUID

/** Identifies whether the [PerfSession] is legacy or not. */
fun PerfSession.isLegacy(): Boolean {
  return this.sessionId().isLegacy()
}

/** Identifies whether the string is from a legacy [PerfSession]. */
fun String.isLegacy(): Boolean {
  return this.startsWith(Constants.UNDEFINED_AQS_ID_PREFIX)
}

/** Creates a valid session ID for [PerfSession] that can be predictably identified as legacy. */
fun createLegacySessionId(): String {
  val uuid = UUID.randomUUID().toString().replace("-", "")
  return uuid.replaceRange(
    0,
    Constants.UNDEFINED_AQS_ID_PREFIX.length,
    Constants.UNDEFINED_AQS_ID_PREFIX
  )
}
