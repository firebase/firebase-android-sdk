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
import com.google.firebase.sessions.SessionEvents.SESSION_EVENT_ENCODER
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeSessionSubscriber
import com.google.firebase.sessions.testing.FakeSettingsProvider
import com.google.firebase.sessions.testing.TestSessionEventData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SessionEventEncoderTest {

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun sessionEvent_encodesToJson() = runTest {
    val fakeFirebaseApp = FakeFirebaseApp()
    val sessionEvent =
      SessionEvents.buildSession(
        fakeFirebaseApp.firebaseApp,
        TestSessionEventData.TEST_SESSION_DETAILS,
        SessionsSettings(
          localOverrideSettings = FakeSettingsProvider(),
          remoteSettings = FakeSettingsProvider(),
        ),
        subscribers =
          mapOf(
            SessionSubscriber.Name.CRASHLYTICS to FakeSessionSubscriber(),
            SessionSubscriber.Name.PERFORMANCE to
              FakeSessionSubscriber(
                isDataCollectionEnabled = false,
                sessionSubscriberName = SessionSubscriber.Name.PERFORMANCE,
              ),
          ),
      )

    val json = SESSION_EVENT_ENCODER.encode(sessionEvent)

    assertThat(json)
      .isEqualTo(
        """
          {
            "eventType":1,
            "sessionData":{
              "sessionId":"a1b2c3",
              "firstSessionId":"a1a1a1",
              "sessionIndex":3,
              "eventTimestampUs":12340000,
              "dataCollectionStatus":{
                "performance":3,
                "crashlytics":2,
                "sessionSamplingRate":1.0
              },
              "firebaseInstallationId":"",
              "firebaseAuthenticationToken":""
            },
            "applicationInfo":{
              "appId":"1:12345:android:app",
              "deviceModel":"${Build.MODEL}",
              "sessionSdkVersion":"${BuildConfig.VERSION_NAME}",
              "osVersion":"${Build.VERSION.RELEASE}",
              "logEnvironment":3,
              "androidAppInfo":{
                "packageName":"com.google.firebase.sessions.test",
                "versionName":"1.0.0",
                "appBuildVersion":"0",
                "deviceManufacturer":"${Build.MANUFACTURER}",
                "currentProcessDetails":{
                  "processName":"com.google.firebase.sessions.test",
                  "pid":0,
                  "importance":100,
                  "defaultProcess":false
                },
                "appProcessDetails":[
                  {
                    "processName":"com.google.firebase.sessions.test",
                    "pid":0,
                    "importance":100,
                    "defaultProcess":false
                  }
                ]
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
            dataCollectionStatus = DataCollectionStatus(),
            firebaseInstallationId = "",
            firebaseAuthenticationToken = "",
          ),
        applicationInfo =
          ApplicationInfo(
            appId = "",
            deviceModel = "",
            sessionSdkVersion = "",
            osVersion = "",
            logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
            AndroidApplicationInfo(
              packageName = "",
              versionName = "",
              appBuildVersion = "",
              deviceManufacturer = "",
              currentProcessDetails = ProcessDetails("", 0, 0, false),
              appProcessDetails = listOf(),
            ),
          ),
      )

    val json = SESSION_EVENT_ENCODER.encode(sessionEvent)

    assertThat(json)
      .isEqualTo(
        """
          {
            "eventType":0,
            "sessionData":{
              "sessionId":"",
              "firstSessionId":"",
              "sessionIndex":0,
              "eventTimestampUs":0,
              "dataCollectionStatus":{
                "performance":1,
                "crashlytics":1,
                "sessionSamplingRate":1.0
              },
              "firebaseInstallationId":"",
              "firebaseAuthenticationToken":""
            },
            "applicationInfo":{
              "appId":"",
              "deviceModel":"",
              "sessionSdkVersion":"",
              "osVersion":"",
              "logEnvironment":3,
              "androidAppInfo":{
                "packageName":"",
                "versionName":"",
                "appBuildVersion":"",
                "deviceManufacturer":"",
                "currentProcessDetails":{
                  "processName":"",
                  "pid":0,
                  "importance":0,
                  "defaultProcess":false
                },
                "appProcessDetails":[]
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
          EventType.SESSION_START,
        )
      )

    assertThat(json).isEqualTo("[1,0,1,1]")
  }
}
