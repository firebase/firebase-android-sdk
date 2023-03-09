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

import com.google.firebase.encoders.ValueEncoder
import com.google.firebase.encoders.ValueEncoderContext
import com.google.firebase.encoders.annotations.Encodable

/**
 * Contains the relevant information around an App Quality Session.
 *
 * See go/app-quality-unified-session-definition for more details. Keep in sync with
 * https://github.com/firebase/firebase-ios-sdk/blob/master/FirebaseSessions/ProtoSupport/Protos/sessions.proto
 */
// TODO(mrober): Add and populate all fields from sessions.proto
// TODO(mrober): Can the firebase-encoders-processor work on Kotlin data classes?
internal data class SessionEvent(
  /** The type of event being reported. */
  val eventType: EventType,

  /** Information about the session triggering the event. */
  val sessionData: SessionInfo,
)

internal enum class EventType(override val number: Int) : NumberedEnum {
  EVENT_TYPE_UNKNOWN(0),
  /** This event type is fired as soon as a new session begins. */
  SESSION_START(1),
}

/** Contains session-specific information relating to the event being uploaded. */
internal data class SessionInfo(
  /** A globally unique identifier for the session. */
  val sessionId: String,

  /**
   * Will point to the first Session for the run of the app.
   *
   * For the first session, this will be the same as session_id.
   */
  val firstSessionId: String,

  /** What order this Session came in this run of the app. For the first Session this will be 0. */
  val sessionIndex: Int,

  /** The data collection status. */
  // TODO(mrober): Refactor to DataCollectionStatus to split out each SDK state, and sampling rate.
  val dataCollectionStatus: Boolean,
)

/** Represents an explicitly numbered proto enum for serialization. */
// TODO(mrober): Could NumberedEnum be part of firebase-encoders?
internal interface NumberedEnum {
  val number: Int

  companion object {
    /** Encode the enum as the number. */
    val ENCODER = ValueEncoder { numberedEnum: NumberedEnum, ctx: ValueEncoderContext ->
      ctx.add(numberedEnum.number)
    }
  }
}
