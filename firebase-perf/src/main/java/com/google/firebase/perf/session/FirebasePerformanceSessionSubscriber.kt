/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.perf.session

import com.google.firebase.perf.config.ConfigResolver
import com.google.firebase.perf.logging.FirebaseSessionsEnforcementCheck
import com.google.firebase.sessions.api.SessionSubscriber

class FirebasePerformanceSessionSubscriber(val configResolver: ConfigResolver) : SessionSubscriber {

  override val sessionSubscriberName: SessionSubscriber.Name = SessionSubscriber.Name.PERFORMANCE

  override val isDataCollectionEnabled: Boolean
    get() = configResolver.isPerformanceCollectionEnabled == true

  override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
    val currentPerfSession = SessionManager.getInstance().perfSession()
    // TODO(b/394127311): Add logic to deal with app start gauges if necessary.
    FirebaseSessionsEnforcementCheck.checkSession(currentPerfSession, "onSessionChanged")

    val updatedSession = PerfSession.createWithId(sessionDetails.sessionId)
    SessionManager.getInstance().updatePerfSession(updatedSession)
  }
}
