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
import androidx.annotation.Discouraged
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.sessions.settings.SessionsSettings
import kotlinx.coroutines.CoroutineDispatcher

class FirebaseSessions
internal constructor(
  private val firebaseApp: FirebaseApp,
  firebaseInstallations: FirebaseInstallationsApi,
  backgroundDispatcher: CoroutineDispatcher,
  transportFactoryProvider: Provider<TransportFactory>,
) {
  private val sessionGenerator = SessionGenerator(collectEvents = true)
  private val eventGDTLogger = EventGDTLogger(transportFactoryProvider)
  private val sessionCoordinator =
    SessionCoordinator(firebaseInstallations, backgroundDispatcher, eventGDTLogger)
  private val sessionSettings = SessionsSettings(firebaseApp.applicationContext)

  init {
    val sessionInitiator =
      SessionInitiator(WallClock::elapsedRealtime, this::initiateSessionStart, sessionSettings)
    val appContext = firebaseApp.applicationContext.applicationContext
    if (appContext is Application) {
      appContext.registerActivityLifecycleCallbacks(sessionInitiator.activityLifecycleCallbacks)
    } else {
      Log.w(
        TAG,
        "Failed to register lifecycle callbacks, unexpected context ${appContext.javaClass}."
      )
    }
  }

  @Discouraged(message = "This will be replaced with a real API.")
  fun greeting(): String = "Matt says hi!"

  private fun initiateSessionStart() {
    val sessionDetails = sessionGenerator.generateNewSession()
    val sessionEvent = SessionEvents.startSession(firebaseApp, sessionDetails, sessionSettings)

    if (sessionDetails.collectEvents) {
      sessionCoordinator.attemptLoggingSessionEvent(sessionEvent)
    }
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
