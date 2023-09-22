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
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.data.FirebaseSessionsDataRepository
import com.google.firebase.sessions.settings.SessionsSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    private val sessionDataRepo = FirebaseSessionsDataRepository(firebaseApp.applicationContext)
    private val timeProvider: TimeProvider = Time()
    private val sessionGenerator: SessionGenerator
    private val eventGDTLogger = EventGDTLogger(transportFactoryProvider)
    private val sessionCoordinator = SessionCoordinator(firebaseInstallations, eventGDTLogger)
    private val isAppDefaultProcess: Boolean

    init {
        sessionGenerator = SessionGenerator(shouldCollectEvents(), timeProvider)
        val appContext = firebaseApp.applicationContext.applicationContext
        if (appContext is Application) {
            isAppDefaultProcess = ProcessUtils.isDefaultProcess(appContext)
            if (isAppDefaultProcess) {
                registerLifecycleCallbacks(
                    appContext, SessionInitiator(
                        timeProvider,
                        backgroundDispatcher,
                        this::initiateSessionStart,
                        sessionSettings,
                        sessionGenerator,
                    ).activityLifecycleCallbacks
                )
            } else {
                Log.d(TAG, "Registering lifecycle callbacks on non-default process: "
                            + "${ProcessUtils.getMyProcessName(appContext)}")
              CoroutineScope(backgroundDispatcher).launch {
                  sessionDataRepo.firebaseSessionDataFlow.collect {
                      if (it.sessionId == null) {
                          Log.d(
                              TAG, "No session id available in shared storage yet." +
                                      " subscribers will not be notified."
                          )
                      } else {
                          notifySubscribers(it.sessionId)
                      }
                  }
              }
            }
        } else {
            isAppDefaultProcess = false
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

    private fun registerLifecycleCallbacks(
        application: Application,
        activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks,
    ) {
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        firebaseApp.addLifecycleEventListener { _, _ ->
            Log.w(
                TAG,
                "FirebaseApp instance deleted. Sessions library will not collect session data."
            )
            application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        }
    }

    private suspend fun notifySubscribers(sessionId: String): Map<SessionSubscriber.Name, SessionSubscriber> {
        val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()
        if (subscribers.isEmpty()) {
            Log.d(
                TAG,
                "Sessions SDK did not have any subscribers. Events will not be sent."
            )
            return subscribers
        }

        subscribers.values.forEach { subscriber ->
            // Notify subscribers, irregardless of sampling and data collection state.
            Log.d(TAG, "Sending session id $sessionId to subscriber $subscriber.")
            subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionId))
        }
        return subscribers
    }

    private suspend fun initiateSessionStart(sessionDetails: SessionDetails) {
        val subscribers = notifySubscribers(sessionDetails.sessionId)

        if (subscribers.isEmpty()) {
            return
        }

        if (subscribers.values.none { it.isDataCollectionEnabled }) {
            Log.d(
                TAG,
                "Data Collection is disabled for all subscribers. Skipping this Session Event"
            )
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
                SessionEvents.startSession(
                    firebaseApp,
                    sessionDetails,
                    sessionSettings,
                    subscribers
                )
            sessionDataRepo.updateSessionId(sessionEvent.sessionData.sessionId)
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

