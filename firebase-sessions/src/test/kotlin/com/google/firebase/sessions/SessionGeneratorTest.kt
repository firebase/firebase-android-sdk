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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.sessions.testing.FakeTimeProvider
import com.google.firebase.sessions.testing.FakeUuidGenerator
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_SESSION_TIMESTAMP
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
  // currentSession before generateNewSession has been called.
  @Test(expected = UninitializedPropertyAccessException::class)
  fun currentSession_beforeGenerate_throwsUninitialized() {
    val sessionGenerator =
      SessionGenerator(timeProvider = FakeTimeProvider(), uuidGenerator = UuidGeneratorImpl)

    sessionGenerator.currentSession
  }

  @Test
  fun hasGenerateSession_beforeGenerate_returnsFalse() {
    val sessionGenerator =
      SessionGenerator(timeProvider = FakeTimeProvider(), uuidGenerator = UuidGeneratorImpl)

    assertThat(sessionGenerator.hasGenerateSession).isFalse()
  }

  @Test
  fun hasGenerateSession_afterGenerate_returnsTrue() {
    val sessionGenerator =
      SessionGenerator(timeProvider = FakeTimeProvider(), uuidGenerator = UuidGeneratorImpl)

    sessionGenerator.generateNewSession()

    assertThat(sessionGenerator.hasGenerateSession).isTrue()
  }

  @Test
  fun generateNewSession_generatesValidSessionIds() {
    val sessionGenerator =
      SessionGenerator(timeProvider = FakeTimeProvider(), uuidGenerator = UuidGeneratorImpl)

    sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(sessionGenerator.currentSession.sessionId)).isTrue()
    assertThat(isValidSessionId(sessionGenerator.currentSession.firstSessionId)).isTrue()

    // Validate several random session ids.
    repeat(16) {
      assertThat(isValidSessionId(sessionGenerator.generateNewSession().sessionId)).isTrue()
    }
  }

  @Test
  fun generateNewSession_generatesValidSessionDetails() {
    val sessionGenerator =
      SessionGenerator(timeProvider = FakeTimeProvider(), uuidGenerator = FakeUuidGenerator())

    sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(sessionGenerator.currentSession.sessionId)).isTrue()
    assertThat(isValidSessionId(sessionGenerator.currentSession.firstSessionId)).isTrue()

    assertThat(sessionGenerator.currentSession)
      .isEqualTo(
        SessionDetails(
          sessionId = SESSION_ID_1,
          firstSessionId = SESSION_ID_1,
          sessionIndex = 0,
          sessionStartTimestampUs = TEST_SESSION_TIMESTAMP.us,
        )
      )
  }

  // Ensures that generating a Session ID multiple times results in the fist
  // Session ID being set in the firstSessionId field
  @Test
  fun generateNewSession_incrementsSessionIndex_keepsFirstSessionId() {
    val sessionGenerator =
      SessionGenerator(timeProvider = FakeTimeProvider(), uuidGenerator = FakeUuidGenerator())

    val firstSessionDetails = sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(firstSessionDetails.sessionId)).isTrue()
    assertThat(isValidSessionId(firstSessionDetails.firstSessionId)).isTrue()

    assertThat(firstSessionDetails)
      .isEqualTo(
        SessionDetails(
          sessionId = SESSION_ID_1,
          firstSessionId = SESSION_ID_1,
          sessionIndex = 0,
          sessionStartTimestampUs = TEST_SESSION_TIMESTAMP.us,
        )
      )

    val secondSessionDetails = sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(secondSessionDetails.sessionId)).isTrue()
    assertThat(isValidSessionId(secondSessionDetails.firstSessionId)).isTrue()

    // Ensure the new firstSessionId is equal to the first sessionId, and sessionIndex increased
    assertThat(secondSessionDetails)
      .isEqualTo(
        SessionDetails(
          sessionId = SESSION_ID_2,
          firstSessionId = SESSION_ID_1,
          sessionIndex = 1,
          sessionStartTimestampUs = TEST_SESSION_TIMESTAMP.us,
        )
      )

    // Do a third round just in case
    val thirdSessionDetails = sessionGenerator.generateNewSession()

    assertThat(isValidSessionId(thirdSessionDetails.sessionId)).isTrue()
    assertThat(isValidSessionId(thirdSessionDetails.firstSessionId)).isTrue()

    assertThat(thirdSessionDetails)
      .isEqualTo(
        SessionDetails(
          sessionId = SESSION_ID_3,
          firstSessionId = SESSION_ID_1,
          sessionIndex = 2,
          sessionStartTimestampUs = TEST_SESSION_TIMESTAMP.us,
        )
      )
  }

  companion object {
    const val SESSION_ID_1 = "11111111111111111111111111111111"
    const val SESSION_ID_2 = "22222222222222222222222222222222"
    const val SESSION_ID_3 = "cccccccccccccccccccccccccccccccc"
  }
}
