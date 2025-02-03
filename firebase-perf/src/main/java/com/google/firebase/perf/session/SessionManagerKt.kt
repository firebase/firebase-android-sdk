package com.google.firebase.perf.session

import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.perf.logging.AndroidLogger


class SessionManagerKt(val dataCollectionEnabled: Boolean): SessionSubscriber {
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

    companion object {
        val perfSessionToAqs: MutableMap<String, SessionSubscriber.SessionDetails?> by lazy {  mutableMapOf() }
    }
}