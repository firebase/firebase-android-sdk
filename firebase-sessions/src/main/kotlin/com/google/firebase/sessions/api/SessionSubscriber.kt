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

package com.google.firebase.sessions.api

import androidx.annotation.Discouraged

/** [SessionSubscriber] is an interface that dependent SDKs must implement. */
interface SessionSubscriber {
  /** [SessionSubscriber.Name]s are used for identifying subscribers. */
  enum class Name {
    CRASHLYTICS,
    PERFORMANCE,
    @Discouraged(message = "This is for testing purposes only.") MATT_SAYS_HI,
  }

  /** [SessionDetails] contains session data passed to subscribers whenever the session changes */
  data class SessionDetails(val sessionId: String)

  fun onSessionChanged(sessionDetails: SessionDetails)

  val isDataCollectionEnabled: Boolean

  val sessionSubscriberName: Name
}
