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
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SessionCoordinatorTest {
  @Test
  fun attemptLoggingSessionEvent_populatesFid() = runTest {
    val sessionCoordinator =
      SessionCoordinator(
        firebaseInstallations = FakeFirebaseInstallations("FaKeFiD"),
        backgroundDispatcher = StandardTestDispatcher(testScheduler),
      )

    // Construct an event with no fid set.
    val sessionEvent =
      SessionEvent(
        eventType = EventType.SESSION_START,
        sessionData =
          SessionInfo(
            sessionId = "id",
            firstSessionId = "first",
            sessionIndex = 3,
          ),
        applicationInfo =
          ApplicationInfo(
            appId = "",
            deviceModel = "",
            sessionSdkVersion = "",
            logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
            androidAppInfo = AndroidApplicationInfo(packageName = "", versionName = "")
          )
      )

    sessionCoordinator.attemptLoggingSessionEvent(sessionEvent)

    runCurrent()

    assertThat(sessionEvent.sessionData.firebaseInstallationId).isEqualTo("FaKeFiD")
  }
}
