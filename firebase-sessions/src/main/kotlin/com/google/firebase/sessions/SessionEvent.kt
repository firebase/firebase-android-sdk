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

import com.google.firebase.encoders.annotations.Encodable
import com.google.firebase.encoders.json.NumberedEnum

/**
 * Contains the relevant information around a Firebase Session Event.
 *
 * See go/app-quality-unified-session-definition for more details. Keep in sync with
 * https://github.com/firebase/firebase-ios-sdk/blob/master/FirebaseSessions/ProtoSupport/Protos/sessions.proto
 */
@Encodable
internal data class SessionEvent(
  /** The type of event being reported. */
  val eventType: EventType,

  /** Information about the session triggering the event. */
  val sessionData: SessionInfo,

  /** Information about the application that is generating the session events. */
  val applicationInfo: ApplicationInfo,
)

/** Enum denoting all possible session event types. */
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

  /** Tracks when the event was initiated. */
  val eventTimestampUs: Long,

  /** Data collection status of the dependent product SDKs. */
  val dataCollectionStatus: DataCollectionStatus,

  /** Identifies a unique device+app installation: go/firebase-installations */
  val firebaseInstallationId: String,

  /** Authentication token for the firebaseInstallationId. */
  val firebaseAuthenticationToken: String,
)

/** Contains the data collection state for all dependent SDKs and sampling info */
internal data class DataCollectionStatus(
  val performance: DataCollectionState = DataCollectionState.COLLECTION_SDK_NOT_INSTALLED,
  val crashlytics: DataCollectionState = DataCollectionState.COLLECTION_SDK_NOT_INSTALLED,
  val sessionSamplingRate: Double = 1.0,
)

/** Container for information about the process */
internal data class ProcessDetails(
  val processName: String,
  val pid: Int,
  val importance: Int,
  val isDefaultProcess: Boolean,
)

/** Enum denoting different data collection states. */
internal enum class DataCollectionState(override val number: Int) : NumberedEnum {
  COLLECTION_UNKNOWN(0),

  // This product SDK is not present in this version of the app.
  COLLECTION_SDK_NOT_INSTALLED(1),

  // The product SDK is present and collecting all product-level events.
  COLLECTION_ENABLED(2),

  // The product SDK is present but data collection for it has been locally
  // disabled.
  COLLECTION_DISABLED(3),

  // The product SDK is present but data collection has been remotely disabled.
  COLLECTION_DISABLED_REMOTE(4),

  // Indicates that the product SDK is present, but session data is being
  // collected, but the product-level events are not being uploaded.
  COLLECTION_SAMPLED(5),
}
