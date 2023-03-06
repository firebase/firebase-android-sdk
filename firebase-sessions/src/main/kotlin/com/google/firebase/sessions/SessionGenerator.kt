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
 * [SessionInfo] is a data class responsible for storing information about the current Session.
 *
 * @hide
 */
internal data class SessionInfo(
  val sessionId: String,
  val firstSessionId: String,
  val collectEvents: Boolean,
  val sessionIndex: Int,
)

/**
 * The [SessionGenerator] is responsible for generating the Session ID, and keeping the
 * [SessionInfo] up to date with the latest values.
 *
 * @hide
 */
internal class SessionGenerator(collectEvents: Boolean) {
  private var firstSessionId = ""
  private var sessionIndex: Int = -1
  private var collectEvents = collectEvents

  private var thisSession: SessionInfo =
    SessionInfo(
      sessionId = "",
      firstSessionId = "",
      collectEvents = collectEvents,
      sessionIndex = sessionIndex
    )

  // Generates a new Session ID. If there was already a generated Session ID
  // from the last session during the app's lifecycle, it will also set the last Session ID
  fun generateNewSession() {
    val newSessionId = UUID.randomUUID().toString().replace("-", "").lowercase()

    // If firstSessionId is set, use it. Otherwise set it to the
    // first generated Session ID
    firstSessionId = if (firstSessionId.isEmpty()) newSessionId else firstSessionId

    sessionIndex += 1

    thisSession =
      SessionInfo(
        sessionId = newSessionId,
        firstSessionId = firstSessionId,
        collectEvents = collectEvents,
        sessionIndex = sessionIndex
      )
  }

  val currentSession: SessionInfo
    get() = thisSession
}
