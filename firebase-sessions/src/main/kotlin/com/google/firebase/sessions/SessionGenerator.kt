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

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.firebase.Firebase
import com.google.firebase.app
import java.util.UUID

/**
 * [SessionDetails] is a data class responsible for storing information about the current Session.
 */
internal data class SessionDetails(
  val sessionId: String,
  val firstSessionId: String,
  val sessionIndex: Int,
  val sessionStartTimestampUs: Long,
)

/**
 * The [SessionGenerator] is responsible for generating the Session ID, and keeping the
 * [SessionDetails] up to date with the latest values.
 */
internal class SessionGenerator(
  private val timeProvider: TimeProvider,
  private val uuidGenerator: () -> UUID = UUID::randomUUID
) {
  private val firstSessionId = generateSessionId()
  private var sessionIndex = -1

  /** The current generated session, must not be accessed before calling [generateNewSession]. */
  lateinit var currentSession: SessionDetails
    private set

  /** Returns if a session has been generated. */
  val hasGenerateSession: Boolean
    get() = ::currentSession.isInitialized

  /** Generates a new session. The first session's sessionId will match firstSessionId. */
  @CanIgnoreReturnValue
  fun generateNewSession(): SessionDetails {
    sessionIndex++
    currentSession =
      SessionDetails(
        sessionId = if (sessionIndex == 0) firstSessionId else generateSessionId(),
        firstSessionId,
        sessionIndex,
        sessionStartTimestampUs = timeProvider.currentTimeUs()
      )
    return currentSession
  }

  private fun generateSessionId() = uuidGenerator().toString().replace("-", "").lowercase()

  internal companion object {
    val instance: SessionGenerator
      get() = Firebase.app[SessionGenerator::class.java]
  }
}
