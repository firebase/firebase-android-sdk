package com.google.firebase.sessions.leader

import android.app.Application
import android.util.Log
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.EventGDTLogger
import com.google.firebase.sessions.SessionCoordinator
import com.google.firebase.sessions.SessionDetails
import com.google.firebase.sessions.SessionEvents
import com.google.firebase.sessions.SessionGenerator
import com.google.firebase.sessions.SessionInitiateListener
import com.google.firebase.sessions.SessionInitiator
import com.google.firebase.sessions.SessionMaintainer
import com.google.firebase.sessions.Time
import com.google.firebase.sessions.TimeProvider
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Maintainer that will manage the full lifecycle of a session including sending this to our
 * backend. It will also notify followers when there is a change
 */
class SessionMaintainerLeader(
  private val firebaseApp: FirebaseApp,
  firebaseInstallations: FirebaseInstallationsApi,
  backgroundDispatcher: CoroutineDispatcher,
  blockingDispatcher: CoroutineDispatcher,
  transportFactoryProvider: Provider<TransportFactory>,
) : SessionMaintainer {

  private val applicationInfo = SessionEvents.getApplicationInfo(firebaseApp)
  private val sessionSettings =
    SessionsSettings(
      firebaseApp.applicationContext,
      blockingDispatcher,
      backgroundDispatcher,
      firebaseInstallations,
      applicationInfo,
    )
  private val timeProvider: TimeProvider = Time()
  private val sessionGenerator =
    SessionGenerator(collectEvents = shouldCollectEvents(), timeProvider)
  private val eventGDTLogger = EventGDTLogger(transportFactoryProvider)
  private val sessionCoordinator = SessionCoordinator(firebaseInstallations, eventGDTLogger)

  private val TAG = "SessionMaintainerLeader"

  override fun start(backgroundDispatcher: CoroutineDispatcher) {
    val sessionInitiateListener =
      object : SessionInitiateListener {
        // Avoid making a public function in FirebaseSessions for onInitiateSession.
        override suspend fun onInitiateSession(sessionDetails: SessionDetails) {
          initiateSessionStart(sessionDetails)
        }
      }

    val sessionInitiator =
      SessionInitiator(
        timeProvider,
        backgroundDispatcher,
        sessionInitiateListener,
        sessionSettings,
        sessionGenerator,
      )

    val appContext = firebaseApp.applicationContext.applicationContext
    if (appContext is Application) {
      appContext.registerActivityLifecycleCallbacks(sessionInitiator.activityLifecycleCallbacks)

      firebaseApp.addLifecycleEventListener { _, _ ->
        Log.w(TAG, "FirebaseApp instance deleted. Sessions library will not collect session data.")
        appContext.unregisterActivityLifecycleCallbacks(sessionInitiator.activityLifecycleCallbacks)
      }
    } else {
      Log.e(
        TAG,
        "Failed to register lifecycle callbacks, unexpected context ${appContext.javaClass}."
      )
    }
  }
  override fun register(subscriber: SessionSubscriber) {
    /*
     * Immediately call the callback if Sessions generated a session before the
     * subscriber subscribed, otherwise subscribers might miss the first session.
     */
    if (sessionGenerator.hasGenerateSession) {
      subscriber.onSessionChanged(
        SessionSubscriber.SessionDetails(sessionGenerator.currentSession.sessionId)
      )
    }
  }

  private suspend fun initiateSessionStart(sessionDetails: SessionDetails) {
    val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()

    if (subscribers.isEmpty()) {
      Log.d(
        TAG,
        "Sessions SDK did not have any dependent SDKs register as dependencies. Events will not be sent."
      )
      return
    }

    subscribers.values.forEach { subscriber ->
      // Notify subscribers, regardless of sampling and data collection state.
      subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionDetails.sessionId))
    }

    if (subscribers.values.none { it.isDataCollectionEnabled }) {
      Log.d(TAG, "Data Collection is disabled for all subscribers. Skipping this Session Event")
      return
    }

    Log.d(TAG, "Data Collection is enabled for at least one Subscriber")

    // This will cause remote settings to be fetched if the cache is expired.
    sessionSettings.updateSettings()

    if (!sessionSettings.sessionsEnabled) {
      Log.d(TAG, "Sessions SDK disabled. Events will not be sent.")
      return
    }

    if (!sessionGenerator.collectEvents) {
      Log.d(TAG, "Sessions SDK has dropped this session due to sampling.")
      return
    }

    try {
      val sessionEvent =
        SessionEvents.startSession(firebaseApp, sessionDetails, sessionSettings, subscribers)
      sessionCoordinator.attemptLoggingSessionEvent(sessionEvent)
    } catch (ex: IllegalStateException) {
      // This can happen if the app suddenly deletes the instance of FirebaseApp.
      Log.w(
        TAG,
        "FirebaseApp is not initialized. Sessions library will not collect session data.",
        ex
      )
    }
  }

  /** Calculate whether we should sample events using [sessionSettings] data. */
  private fun shouldCollectEvents(): Boolean {
    // Sampling rate of 1 means the SDK will send every event.
    val randomValue = Math.random()
    return randomValue <= sessionSettings.samplingRate
  }
}
