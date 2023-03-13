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
import org.junit.Test

class SessionGeneratorTest {
  private fun isValidSessionId(sessionId: String): Boolean {
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
    val sessionGenerator = SessionGenerator(collectEvents = false)

    assertThat(sessionGenerator.currentSession.sessionId).isEqualTo("")
    assertThat(sessionGenerator.currentSession.firstSessionId).isEqualTo("")
    assertThat(sessionGenerator.currentSession.collectEvents).isFalse()
    assertThat(sessionGenerator.currentSession.sessionIndex).isEqualTo(-1)
  }

  @Test
  fun generateNewSessionID_generatesValidSessionDetails() {
    val sessionGenerator = SessionGenerator(collectEvents = true)

    sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(sessionGenerator.currentSession.sessionId)).isTrue()
    assertThat(isValidSessionId(sessionGenerator.currentSession.firstSessionId)).isTrue()
    assertThat(sessionGenerator.currentSession.firstSessionId)
      .isEqualTo(sessionGenerator.currentSession.sessionId)
    assertThat(sessionGenerator.currentSession.collectEvents).isTrue()
    assertThat(sessionGenerator.currentSession.sessionIndex).isEqualTo(0)
  }

  // Ensures that generating a Session ID multiple times results in the fist
  // Session ID being set in the firstSessionId field
  @Test
  fun generateNewSessionID_incrementsSessionIndex_keepsFirstSessionId() {
    val sessionGenerator = SessionGenerator(collectEvents = true)

    sessionGenerator.generateNewSession()

    val firstSessionDetails = sessionGenerator.currentSession

    assertThat(isValidSessionId(firstSessionDetails.sessionId)).isTrue()
    assertThat(isValidSessionId(firstSessionDetails.firstSessionId)).isTrue()
    assertThat(firstSessionDetails.firstSessionId).isEqualTo(firstSessionDetails.sessionId)
    assertThat(firstSessionDetails.sessionIndex).isEqualTo(0)

    sessionGenerator.generateNewSession()
    val secondSessionDetails = sessionGenerator.currentSession

    assertThat(isValidSessionId(secondSessionDetails.sessionId)).isTrue()
    assertThat(isValidSessionId(secondSessionDetails.firstSessionId)).isTrue()
    // Ensure the new firstSessionId is equal to the first Session ID from earlier
    assertThat(secondSessionDetails.firstSessionId).isEqualTo(firstSessionDetails.sessionId)
    // Session Index should increase
    assertThat(secondSessionDetails.sessionIndex).isEqualTo(1)

    // Do a third round just in case
    sessionGenerator.generateNewSession()
    val thirdSessionDetails = sessionGenerator.currentSession

    assertThat(isValidSessionId(thirdSessionDetails.sessionId)).isTrue()
    assertThat(isValidSessionId(thirdSessionDetails.firstSessionId)).isTrue()
    // Ensure the new firstSessionId is equal to the first Session ID from earlier
    assertThat(thirdSessionDetails.firstSessionId).isEqualTo(firstSessionDetails.sessionId)
    // Session Index should increase
    assertThat(thirdSessionDetails.sessionIndex).isEqualTo(2)
  }
}
