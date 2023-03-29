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
import com.google.firebase.sessions.SessionEvents.SESSION_EVENT_ENCODER
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionEventEncoderTest {
  @Test
  fun sessionEvent_encodesToJson() {
    val sessionEvent =
      SessionEvent(
        eventType = EventType.SESSION_START,
        sessionData =
          SessionInfo(
            sessionId = "id",
            firstSessionId = "first",
            sessionIndex = 9,
            firebaseInstallationId = "fid"
          ),
        applicationInfo =
          ApplicationInfo(
            appId = "",
            deviceModel = "",
            sessionSdkVersion = "",
            logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
            AndroidApplicationInfo(packageName = "", versionName = ""),
          )
      )

    val json = SESSION_EVENT_ENCODER.encode(sessionEvent)

    assertThat(json)
      .isEqualTo(
        """
        {
          "event_type":1,
          "session_data":{
            "session_id":"id",
            "first_session_id":"first",
            "session_index":9,
            "firebase_installation_id":"fid"
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
          ),
        applicationInfo =
          ApplicationInfo(
            appId = "",
            deviceModel = "",
            sessionSdkVersion = "",
            logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
            AndroidApplicationInfo(packageName = "", versionName = ""),
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
            "firebase_installation_id":""
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
