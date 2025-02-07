package com.google.firebase.perf.session

import com.google.firebase.perf.session.gauges.GaugeManager
import com.google.firebase.perf.v1.ApplicationProcessState
import com.google.firebase.sessions.api.SessionSubscriber
import java.util.UUID

class FirebasePerformanceSessionSubscriber(private val dataCollectionEnabled: Boolean) :
  SessionSubscriber {
  override val isDataCollectionEnabled: Boolean
    get() = dataCollectionEnabled

  override val sessionSubscriberName: SessionSubscriber.Name
    get() = SessionSubscriber.Name.PERFORMANCE

  override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
    val currentPerfSession = SessionManager.getInstance().perfSession()

    // A [PerfSession] was created before a session was started.
    if (currentPerfSession.aqsSessionId() == null) {
      currentPerfSession.setAQSId(sessionDetails)
      GaugeManager.getInstance()
        .logGaugeMetadata(currentPerfSession.aqsSessionId(), ApplicationProcessState.FOREGROUND)
      return
    }

    val updatedSession = PerfSession.createWithId(UUID.randomUUID().toString())
    updatedSession.setAQSId(sessionDetails)
    SessionManager.getInstance().updatePerfSession(updatedSession)
    GaugeManager.getInstance()
      .logGaugeMetadata(updatedSession.aqsSessionId(), ApplicationProcessState.FOREGROUND)
  }
}
