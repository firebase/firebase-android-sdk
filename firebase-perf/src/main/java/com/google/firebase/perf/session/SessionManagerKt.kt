package com.google.firebase.perf.session

import com.google.firebase.perf.config.ConfigResolver
import com.google.firebase.perf.logging.AndroidLogger
import com.google.firebase.sessions.api.SessionSubscriber

class SessionManagerKt(private val dataCollectionEnabled: Boolean) : SessionSubscriber {
  private val perfSessionToAqs: MutableMap<String, SessionSubscriber.SessionDetails?> =
    mutableMapOf()

  override val isDataCollectionEnabled: Boolean
    get() = dataCollectionEnabled

  override val sessionSubscriberName: SessionSubscriber.Name
    get() = SessionSubscriber.Name.PERFORMANCE

  override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
    AndroidLogger.getInstance().debug("AQS Session Changed: $sessionDetails")
    val currentInternalSessionId = SessionManager.getInstance().perfSession().internalSessionId

    // There can be situations where a new [PerfSession] was created, but an AQS wasn't
    // available (during cold start).
    if (perfSessionToAqs[currentInternalSessionId] == null) {
      perfSessionToAqs[currentInternalSessionId] = sessionDetails
    } else {
      val newSession = PerfSession.createNewSession()
      SessionManager.getInstance().updatePerfSession(newSession)
      perfSessionToAqs[newSession.internalSessionId] = sessionDetails
    }
  }

  fun reportPerfSession(perfSessionId: String) {
    perfSessionToAqs[perfSessionId] = null
  }

  fun getAqsMappedToPerfSession(perfSessionId: String): String {
    AndroidLogger.getInstance()
      .debug("AQS for perf session $perfSessionId is ${perfSessionToAqs[perfSessionId]?.sessionId}")
    return perfSessionToAqs[perfSessionId]?.sessionId ?: perfSessionId
  }

  companion object {
    val instance: SessionManagerKt by lazy {
      SessionManagerKt(ConfigResolver.getInstance().isPerformanceMonitoringEnabled)
    }
  }
}
