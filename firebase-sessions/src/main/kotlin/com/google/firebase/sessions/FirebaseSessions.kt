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

import android.app.Application
import android.util.Log
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings
import kotlinx.coroutines.CoroutineDispatcher

/** The [FirebaseSessions] API provides methods to register a [SessionSubscriber]. */
class FirebaseSessions
internal constructor(
  private val firebaseApp: FirebaseApp,
  firebaseInstallations: FirebaseInstallationsApi,
  backgroundDispatcher: CoroutineDispatcher,
  blockingDispatcher: CoroutineDispatcher,
  transportFactoryProvider: Provider<TransportFactory>,
) {
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
  private val sessionGenerator: SessionGenerator
  private val eventGDTLogger = EventGDTLogger(transportFactoryProvider)
  private val sessionCoordinator = SessionCoordinator(firebaseInstallations, eventGDTLogger)

  init {
    sessionGenerator = SessionGenerator(collectEvents = shouldCollectEvents(), timeProvider)

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

  /** Register the [subscriber]. This must be called for every dependency. */
  fun register(subscriber: SessionSubscriber) {
    FirebaseSessionsDependencies.register(subscriber)

    Log.d(
      TAG,
      "Registering Sessions SDK subscriber with name: ${subscriber.sessionSubscriberName}, " +
        "data collection enabled: ${subscriber.isDataCollectionEnabled}"
    )

    // Immediately call the callback if Sessions generated a session before the
    // subscriber subscribed, otherwise subscribers might miss the first session.
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

  companion object {
    private const val TAG = "FirebaseSessions"

    @JvmStatic
    val instance: FirebaseSessions
      get() = getInstance(Firebase.app)

    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseSessions = app.get(FirebaseSessions::class.java)
  }
}
