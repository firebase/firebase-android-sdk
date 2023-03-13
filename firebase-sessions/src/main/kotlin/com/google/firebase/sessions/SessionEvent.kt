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

/**
 * Contains the relevant information around a Firebase Session Event.
 *
 * See go/app-quality-unified-session-definition for more details. Keep in sync with
 * https://github.com/firebase/firebase-ios-sdk/blob/master/FirebaseSessions/ProtoSupport/Protos/sessions.proto
 */
// TODO(mrober): Add and populate all fields from sessions.proto
internal data class SessionEvent(
  /** The type of event being reported. */
  val eventType: EventType,

  /** Information about the session triggering the event. */
  val sessionData: SessionInfo,
) {
  companion object {
    fun sessionStart(sessionDetails: SessionDetails) =
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
}

/** Enum denoting all possible session event types. */
internal enum class EventType(val number: Int) {
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
)
