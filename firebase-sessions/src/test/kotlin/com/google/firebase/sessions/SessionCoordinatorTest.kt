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
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeEventGDTLogger
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeTimeProvider
import com.google.firebase.sessions.testing.TestSessionEventData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SessionCoordinatorTest {
  @Test
  fun attemptLoggingSessionEvent_populatesFid() = runTest {
    val fakeEventGDTLogger = FakeEventGDTLogger()
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val sessionCoordinator =
      SessionCoordinator(
        firebaseInstallations,
        context = TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        eventGDTLogger = fakeEventGDTLogger,
      )

    // Construct an event with no fid set.
    val fakeFirebaseApp = FakeFirebaseApp()
    val sessionEvent =
      SessionEvents.startSession(
        fakeFirebaseApp.firebaseApp,
        TestSessionEventData.TEST_SESSION_DETAILS,
        SessionsSettings(
          fakeFirebaseApp.firebaseApp.applicationContext,
          firebaseInstallations,
          SessionEvents.getApplicationInfo(fakeFirebaseApp.firebaseApp)
        ),
        FakeTimeProvider(),
      )

    sessionCoordinator.attemptLoggingSessionEvent(sessionEvent)

    runCurrent()

    assertThat(sessionEvent.sessionData.firebaseInstallationId).isEqualTo("FaKeFiD")
    assertThat(fakeEventGDTLogger.loggedEvent!!.sessionData.firebaseInstallationId)
      .isEqualTo("FaKeFiD")
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
