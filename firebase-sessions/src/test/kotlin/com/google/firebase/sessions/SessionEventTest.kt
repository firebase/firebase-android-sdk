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
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.TestSessionEventData
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionEventTest {
  @Test
  fun sessionStart_populatesSessionDetailsCorrectly() {
    val sessionEvent =
      SessionEvents.startSession(
        FakeFirebaseApp.fakeFirebaseApp(),
        TestSessionEventData.TEST_SESSION_DETAILS,
        TestSessionEventData.TEST_SESSION_TIMESTAMP_US,
      )

    assertThat(sessionEvent).isEqualTo(TestSessionEventData.EXPECTED_DEFAULT_SESSION_EVENT)
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
