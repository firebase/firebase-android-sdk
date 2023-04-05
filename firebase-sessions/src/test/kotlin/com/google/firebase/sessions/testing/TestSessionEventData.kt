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

package com.google.firebase.sessions.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.sessions.*

internal object TestSessionEventData {
  val TEST_SESSION_DETAILS =
    SessionDetails(
      sessionId = "a1b2c3",
      firstSessionId = "a1a1a1",
      collectEvents = true,
      sessionIndex = 3,
    )

  val EXPECTED_DEFAULT_SESSION_EVENT =
    SessionEvent(
      eventType = EventType.SESSION_START,
      sessionData =
        SessionInfo(
          sessionId = "a1b2c3",
          firstSessionId = "a1a1a1",
          sessionIndex = 3,
        ),
      applicationInfo =
        ApplicationInfo(
          appId = FakeFirebaseApp.MOCK_APP_ID,
          deviceModel = "",
          sessionSdkVersion = BuildConfig.VERSION_NAME,
          logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
          AndroidApplicationInfo(
            packageName = ApplicationProvider.getApplicationContext<Context>().packageName,
            versionName = FakeFirebaseApp.MOCK_APP_VERSION
          ),
        )
    )
}
