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

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

class SessionGeneratorTest {
  fun isValidSessionId(sessionId: String): Boolean {
    if (sessionId.length != 32) {
      return false
    }
    if (sessionId.contains("-")) {
      return false
    }
    if (sessionId.lowercase() != sessionId) {
      return false
    }
    return true
  }

  // This test case isn't important behavior. Nothing should access
  // currentSession before generateNewSession has been called. This test just
  // ensures it has consistent behavior.
  @Test
  fun currentSession_beforeGenerateReturnsDefault() {
    val sessionGenerator = SessionGenerator(false)

    assertThat(sessionGenerator.currentSession.sessionId).isEqualTo("")
    assertThat(sessionGenerator.currentSession.firstSessionId).isEqualTo("")
    assertThat(sessionGenerator.currentSession.shouldDispatchEvents).isEqualTo(false)
    assertThat(sessionGenerator.currentSession.sessionIndex).isEqualTo(-1)
  }

  @Test
  fun generateNewSessionID_generatesValidSessionInfo() {
    val sessionGenerator = SessionGenerator(true)

    sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(sessionGenerator.currentSession.sessionId)).isEqualTo(true)
    assertThat(isValidSessionId(sessionGenerator.currentSession.firstSessionId)).isEqualTo(true)
    assertThat(sessionGenerator.currentSession.firstSessionId).isEqualTo(sessionGenerator.currentSession.sessionId)
    assertThat(sessionGenerator.currentSession.shouldDispatchEvents).isEqualTo(true)
    assertThat(sessionGenerator.currentSession.sessionIndex).isEqualTo(0)
  }

  // Ensures that generating a Session ID multiple times results in the fist
  // Session ID being set in the firstSessionId field
  @Test
  fun generateNewSessionID_incrementsSessionIndex_keepsFirstSessionId() {
    val sessionGenerator = SessionGenerator(true)

    sessionGenerator.generateNewSession()

    val firstSessionInfo = sessionGenerator.currentSession

    assertThat(isValidSessionId(firstSessionInfo.sessionId)).isEqualTo(true)
    assertThat(isValidSessionId(firstSessionInfo.firstSessionId)).isEqualTo(true)
    assertThat(firstSessionInfo.firstSessionId).isEqualTo(firstSessionInfo.sessionId)
    assertThat(firstSessionInfo.sessionIndex).isEqualTo(0)

    sessionGenerator.generateNewSession()
    val secondSessionInfo = sessionGenerator.currentSession

    assertThat(isValidSessionId(secondSessionInfo.sessionId)).isEqualTo(true)
    assertThat(isValidSessionId(secondSessionInfo.firstSessionId)).isEqualTo(true)
    // Ensure the new firstSessionId is equal to the first Session ID from earlier
    assertThat(secondSessionInfo.firstSessionId).isEqualTo(firstSessionInfo.sessionId)
    // Session Index should increase
    assertThat(secondSessionInfo.sessionIndex).isEqualTo(1)

    // Do a third round just in case
    sessionGenerator.generateNewSession()
    val thirdSessionInfo = sessionGenerator.currentSession

    assertThat(isValidSessionId(thirdSessionInfo.sessionId)).isEqualTo(true)
    assertThat(isValidSessionId(thirdSessionInfo.firstSessionId)).isEqualTo(true)
    // Ensure the new firstSessionId is equal to the first Session ID from earlier
    assertThat(thirdSessionInfo.firstSessionId).isEqualTo(firstSessionInfo.sessionId)
    // Session Index should increase
    assertThat(thirdSessionInfo.sessionIndex).isEqualTo(2)
  }
}
