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
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.sessions.AndroidApplicationInfo
import com.google.firebase.sessions.ApplicationInfo
import com.google.firebase.sessions.BuildConfig
import com.google.firebase.sessions.DataCollectionState
import com.google.firebase.sessions.DataCollectionStatus
import com.google.firebase.sessions.EventType
import com.google.firebase.sessions.LogEnvironment
import com.google.firebase.sessions.ProcessDetails
import com.google.firebase.sessions.SessionDetails
import com.google.firebase.sessions.SessionEvent
import com.google.firebase.sessions.SessionInfo

internal object TestSessionEventData {
  const val TEST_SESSION_TIMESTAMP_US: Long = 12340000

  val TEST_SESSION_DETAILS =
    SessionDetails(
      sessionId = "a1b2c3",
      firstSessionId = "a1a1a1",
      sessionIndex = 3,
      sessionStartTimestampUs = TEST_SESSION_TIMESTAMP_US
    )

  val TEST_DATA_COLLECTION_STATUS =
    DataCollectionStatus(
      performance = DataCollectionState.COLLECTION_SDK_NOT_INSTALLED,
      crashlytics = DataCollectionState.COLLECTION_SDK_NOT_INSTALLED,
      sessionSamplingRate = 1.0
    )

  val TEST_SESSION_DATA =
    SessionInfo(
      sessionId = "a1b2c3",
      firstSessionId = "a1a1a1",
      sessionIndex = 3,
      eventTimestampUs = TEST_SESSION_TIMESTAMP_US,
      dataCollectionStatus = TEST_DATA_COLLECTION_STATUS,
      firebaseInstallationId = "",
      firebaseAuthenticationToken = "",
    )

  val TEST_PROCESS_DETAILS =
    ProcessDetails(
      processName = "com.google.firebase.sessions.test",
      0,
      100,
      false,
    )

  val TEST_APP_PROCESS_DETAILS = listOf(TEST_PROCESS_DETAILS)

  val TEST_APPLICATION_INFO =
    ApplicationInfo(
      appId = FakeFirebaseApp.MOCK_APP_ID,
      deviceModel = Build.MODEL,
      sessionSdkVersion = BuildConfig.VERSION_NAME,
      osVersion = Build.VERSION.RELEASE,
      logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
      AndroidApplicationInfo(
        packageName = ApplicationProvider.getApplicationContext<Context>().packageName,
        versionName = FakeFirebaseApp.MOCK_APP_VERSION,
        appBuildVersion = FakeFirebaseApp.MOCK_APP_BUILD_VERSION,
        deviceManufacturer = Build.MANUFACTURER,
        currentProcessDetails = TEST_PROCESS_DETAILS,
        appProcessDetails = TEST_APP_PROCESS_DETAILS,
      ),
    )

  val TEST_SESSION_EVENT =
    SessionEvent(
      eventType = EventType.SESSION_START,
      sessionData = TEST_SESSION_DATA,
      applicationInfo = TEST_APPLICATION_INFO,
    )
}
