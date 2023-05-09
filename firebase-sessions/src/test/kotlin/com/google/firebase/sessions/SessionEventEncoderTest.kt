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

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.SessionEvents.SESSION_EVENT_ENCODER
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeTimeProvider
import com.google.firebase.sessions.testing.TestSessionEventData
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionEventEncoderTest {

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun sessionEvent_encodesToJson() = runTest {
    val fakeFirebaseApp = FakeFirebaseApp()
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val sessionEvent =
      SessionEvents.startSession(
        fakeFirebaseApp.firebaseApp,
        TestSessionEventData.TEST_SESSION_DETAILS,
        SessionsSettings(
          fakeFirebaseApp.firebaseApp.applicationContext,
          TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext,
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
          firebaseInstallations,
          SessionEvents.getApplicationInfo(fakeFirebaseApp.firebaseApp)
        ),
        FakeTimeProvider(),
      )

    val json = SESSION_EVENT_ENCODER.encode(sessionEvent)

    assertThat(json)
      .isEqualTo(
        """
          {
            "event_type":1,
            "session_data":{
              "session_id":"a1b2c3",
              "first_session_id":"a1a1a1",
              "session_index":3,
              "firebase_installation_id":"",
              "event_timestamp_us":12340000,
              "data_collection_status":{
                "performance":2,
                "crashlytics":2,
                "session_sampling_rate":1.0
              }
            },
            "application_info":{
              "app_id":"1:12345:android:app",
              "device_model":"${Build.MODEL}",
              "session_sdk_version":"0.1.0",
              "os_version":"${Build.VERSION.SDK_INT}",
              "log_environment":3,
              "android_app_info":{
                "package_name":"com.google.firebase.sessions.test",
                "version_name":"1.0.0",
                "app_build_version":"5",
                "device_manufacturer":"${Build.MANUFACTURER}"
              }
            }
          }
        """
          .lines()
          .joinToString("") { it.trim() }
      )
  }

  @Test
  fun sessionEvent_emptyValues_encodesToJson() {
    val sessionEvent =
      SessionEvent(
        eventType = EventType.EVENT_TYPE_UNKNOWN,
        sessionData =
          SessionInfo(
            sessionId = "",
            firstSessionId = "",
            sessionIndex = 0,
            eventTimestampUs = 0,
          ),
        applicationInfo =
          ApplicationInfo(
            appId = "",
            deviceModel = "",
            sessionSdkVersion = "",
            os_version = "",
            logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
            AndroidApplicationInfo(
              packageName = "",
              versionName = "",
              app_build_version = "",
              device_manufacturer = ""
            ),
          )
      )

    val json = SESSION_EVENT_ENCODER.encode(sessionEvent)

    assertThat(json)
      .isEqualTo(
        """
          {
            "event_type":0,
            "session_data":{
              "session_id":"",
              "first_session_id":"",
              "session_index":0,
              "firebase_installation_id":"",
              "event_timestamp_us":0,
              "data_collection_status":{
                "performance":2,
                "crashlytics":2,
                "session_sampling_rate":1.0
              }
            },
            "application_info":{
              "app_id":"",
              "device_model":"",
              "session_sdk_version":"",
              "os_version":"",
              "log_environment":3,
              "android_app_info":{
                "package_name":"",
                "version_name":"",
                "app_build_version":"",
                "device_manufacturer":""
              }
            }
          }
        """
          .lines()
          .joinToString("") { it.trim() }
      )
  }

  @Test
  fun eventType_numberedEnum_encodesToJson() {
    val json =
      SESSION_EVENT_ENCODER.encode(
        arrayOf(
          EventType.SESSION_START,
          EventType.EVENT_TYPE_UNKNOWN,
          EventType.SESSION_START,
          EventType.SESSION_START
        )
      )

    assertThat(json).isEqualTo("[1,0,1,1]")
  }
}
