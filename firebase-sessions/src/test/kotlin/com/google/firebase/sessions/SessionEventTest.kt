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

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_DATA_COLLECTION_STATUS
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_SESSION_DATA
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_SESSION_DETAILS
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_SESSION_EVENT
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_SESSION_TIMESTAMP_US
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionEventTest {
  @Test
  fun sessionStart_populatesSessionDetailsCorrectly() {
    val fakeFirebaseApp = FakeFirebaseApp()
    val sessionEvent =
      SessionEvents.startSession(
        fakeFirebaseApp.firebaseApp,
        TEST_SESSION_DETAILS,
        SessionsSettings(fakeFirebaseApp.firebaseApp.applicationContext),
        TEST_SESSION_TIMESTAMP_US,
      )

    assertThat(sessionEvent).isEqualTo(TEST_SESSION_EVENT)
  }

  @Test
  fun sessionStart_samplingRate() {
    val metadata = Bundle()
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    val fakeFirebaseApp = FakeFirebaseApp(metadata)

    val sessionEvent =
      SessionEvents.startSession(
        fakeFirebaseApp.firebaseApp,
        TEST_SESSION_DETAILS,
        SessionsSettings(fakeFirebaseApp.firebaseApp.applicationContext),
        TEST_SESSION_TIMESTAMP_US,
      )

    assertThat(sessionEvent)
      .isEqualTo(
        TEST_SESSION_EVENT.copy(
          sessionData =
            TEST_SESSION_DATA.copy(
              dataCollectionStatus = TEST_DATA_COLLECTION_STATUS.copy(sessionSamplingRate = 0.5)
            )
        )
      )
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
