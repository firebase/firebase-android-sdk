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

import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.api.SessionSubscriber.Name.CRASHLYTICS

/** Fake [SessionSubscriber] that can set [isDataCollectionEnabled] and [sessionSubscriberName]. */
internal class FakeSessionSubscriber(
  override val isDataCollectionEnabled: Boolean = true,
  override val sessionSubscriberName: SessionSubscriber.Name = CRASHLYTICS,
) : SessionSubscriber {

  val sessionChangedEvents = mutableListOf<SessionSubscriber.SessionDetails>()

  override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
    sessionChangedEvents.add(sessionDetails)
  }
}
