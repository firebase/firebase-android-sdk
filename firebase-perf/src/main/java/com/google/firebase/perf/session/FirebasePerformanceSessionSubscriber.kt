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

import com.google.firebase.perf.session.gauges.GaugeManager
import com.google.firebase.perf.v1.ApplicationProcessState
import com.google.firebase.sessions.api.SessionSubscriber
import java.util.UUID

class FirebasePerformanceSessionSubscriber(override val isDataCollectionEnabled: Boolean) :
  SessionSubscriber {

  override val sessionSubscriberName: SessionSubscriber.Name = SessionSubscriber.Name.PERFORMANCE

  override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
    val sessionManager = SessionManager.getInstance()
    val currentPerfSession = sessionManager.perfSession()
    val gaugeManager = GaugeManager.getInstance()

    // A [PerfSession] was created before a session was started.
    if (currentPerfSession.aqsSessionId() == null) {
      currentPerfSession.setAQSId(sessionDetails)
      gaugeManager
        .logGaugeMetadata(currentPerfSession.aqsSessionId(), ApplicationProcessState.FOREGROUND)
      gaugeManager.updateGaugeCollection(ApplicationProcessState.FOREGROUND)
      return
    }

    val updatedSession = PerfSession.createWithId(UUID.randomUUID().toString())
    updatedSession.setAQSId(sessionDetails)
    sessionManager.updatePerfSession(updatedSession)
    gaugeManager
      .logGaugeMetadata(updatedSession.aqsSessionId(), ApplicationProcessState.FOREGROUND)
  }
}
