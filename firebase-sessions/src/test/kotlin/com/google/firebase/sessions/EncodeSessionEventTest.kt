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
import com.google.firebase.encoders.FieldDescriptor
import com.google.firebase.encoders.ObjectEncoderContext
import com.google.firebase.encoders.json.JsonDataEncoderBuilder
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncodeSessionEventTest {

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
            dataCollectionStatus = true,
          ),
      )

    val dataEncoder =
      JsonDataEncoderBuilder()
        .configureWith {
          it.registerEncoder(EventType::class.java, NumberedEnum.ENCODER)

          it.registerEncoder(SessionEvent::class.java) {
            sessionEvent: SessionEvent,
            ctx: ObjectEncoderContext ->
            run {
              ctx.add(FieldDescriptor.of("event_type"), sessionEvent.eventType)
              ctx.add(FieldDescriptor.of("session_data"), sessionEvent.sessionData)
            }
          }

          it.registerEncoder(SessionInfo::class.java) {
            sessionInfo: SessionInfo,
            ctx: ObjectEncoderContext ->
            run {
              ctx.add(FieldDescriptor.of("session_id"), sessionInfo.sessionId)
              ctx.add(FieldDescriptor.of("first_session_id"), sessionInfo.firstSessionId)
              ctx.add(FieldDescriptor.of("session_index"), sessionInfo.sessionIndex)
              ctx.add(
                FieldDescriptor.of("data_collection_status"),
                sessionInfo.dataCollectionStatus
              )
            }
          }
        }
        .build()

    val json = dataEncoder.encode(sessionEvent)

    // TODO(mrober): Is there something like assertThat(str).ignoringWhiteSpace().isEqualTo(... ?
    assertThat(json)
      .isEqualTo(
        """
          {
            "event_type":1,
            "session_data":{
              "session_id":"id",
              "first_session_id":"first",
              "session_index":9,
              "data_collection_status":true
            }
          }
        """
          .replace("\\s".toRegex(), "") // compare ignoring all white space
      )
  }
}
