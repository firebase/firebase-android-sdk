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

import com.google.firebase.encoders.DataEncoder
import com.google.firebase.encoders.FieldDescriptor
import com.google.firebase.encoders.ObjectEncoderContext
import com.google.firebase.encoders.json.JsonDataEncoderBuilder

/**
 * Contains functions for [SessionEvent]s.
 *
 * @hide
 */
internal object SessionEvents {
  /** JSON [DataEncoder] for [SessionEvent]s. */
  // TODO(mrober): Replace with firebase-encoders-processor when it can encode Kotlin data classes.
  internal val SESSION_EVENT_ENCODER: DataEncoder =
    JsonDataEncoderBuilder()
      .configureWith {
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
              FieldDescriptor.of("firebase_installation_id"),
              sessionInfo.firebaseInstallationId
            )
          }
        }
      }
      .build()

  /**
   * Construct a Session Start event.
   *
   * Some mutable fields, e.g. firebaseInstallationId, get populated later.
   */
  fun startSession(sessionDetails: SessionDetails) =
    SessionEvent(
      eventType = EventType.SESSION_START,
      sessionData =
        SessionInfo(
          sessionDetails.sessionId,
          sessionDetails.firstSessionId,
          sessionDetails.sessionIndex,
        ),
    )
}
