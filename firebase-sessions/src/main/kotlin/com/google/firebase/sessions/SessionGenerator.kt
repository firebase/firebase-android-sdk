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

import java.util.UUID

/**
 * [SessionDetails] is a data class responsible for storing information about the current Session.
 *
 * @hide
 */
internal data class SessionDetails(
  val sessionId: String,
  val firstSessionId: String,
  val collectEvents: Boolean,
  val sessionIndex: Int,
)

/**
 * The [SessionGenerator] is responsible for generating the Session ID, and keeping the
 * [SessionDetails] up to date with the latest values.
 *
 * @hide
 */
internal class SessionGenerator(private val collectEvents: Boolean) {
  private val firstSessionId = generateSessionId()
  private var sessionIndex = 0

  var currentSession =
    SessionDetails(sessionId = "", firstSessionId = "", collectEvents, sessionIndex = -1)
    private set

  /** Generates a new session. The first session's sessionId will match firstSessionId. */
  fun generateNewSession(): SessionDetails {
    currentSession =
      SessionDetails(
        sessionId = if (sessionIndex == 0) firstSessionId else generateSessionId(),
        firstSessionId,
        collectEvents,
        sessionIndex++,
      )
    return currentSession
  }

  private fun generateSessionId() = UUID.randomUUID().toString().replace("-", "").lowercase()
}
