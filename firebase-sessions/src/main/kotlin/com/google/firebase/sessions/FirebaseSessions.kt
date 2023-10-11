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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber

/** The [FirebaseSessions] API provides the function to register a [SessionSubscriber]. */
class FirebaseSessions internal constructor(private val sessionMaintainer: SessionMaintainer) {
  init {
    val sessionInitiator =
      SessionInitiator(
        timeProvider = WallClock,
        sessionMaintainer.backgroundDispatcher,
        sessionInitiateListener = { sessionDetails ->
          // TODO: Let the service decide when to call which one.
          notifySubscribers(sessionDetails)
          sessionMaintainer.initiateSessionStart(sessionDetails)
        },
        sessionMaintainer.sessionSettings,
        sessionMaintainer.sessionGenerator,
      )

    val appContext = sessionMaintainer.firebaseApp.applicationContext.applicationContext
    if (appContext is Application) {
      SessionDataService.bind(appContext)
      appContext.registerActivityLifecycleCallbacks(sessionInitiator.activityLifecycleCallbacks)

      sessionMaintainer.firebaseApp.addLifecycleEventListener { _, _ ->
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
    if (sessionMaintainer.sessionGenerator.hasGenerateSession) {
      subscriber.onSessionChanged(
        SessionSubscriber.SessionDetails(
          sessionMaintainer.sessionGenerator.currentSession.sessionId
        )
      )
    }
  }

  /** Notify subscribers, regardless of sampling and data collection state. */
  private suspend fun notifySubscribers(sessionDetails: SessionDetails) =
    FirebaseSessionsDependencies.getRegisteredSubscribers().values.forEach { subscriber ->
      subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionDetails.sessionId))
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
