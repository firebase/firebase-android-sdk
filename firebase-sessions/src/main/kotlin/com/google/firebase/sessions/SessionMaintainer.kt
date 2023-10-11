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

import android.util.Log
import com.google.android.datatransport.TransportFactory
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.settings.SessionsSettings
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [SessionMaintainer] is responsible for coordinating the systems in this SDK involved with sending
 * a [SessionEvent].
 */
internal class SessionMaintainer(
  val firebaseApp: FirebaseApp,
  firebaseInstallations: FirebaseInstallationsApi,
  val backgroundDispatcher: CoroutineDispatcher,
  blockingDispatcher: CoroutineDispatcher,
  transportFactoryProvider: Provider<TransportFactory>,
) {
  // TODO: Settings needs to be moved to the Service so there is one instance in multi-process apps.
  val sessionSettings =
    SessionsSettings(
      firebaseApp.applicationContext,
      blockingDispatcher,
      backgroundDispatcher,
      firebaseInstallations,
      SessionEvents.getApplicationInfo(firebaseApp),
    )
  val sessionGenerator: SessionGenerator

  // TODO(mrober): Merge SessionCoordinator into here.
  private val eventGDTLogger = EventGDTLogger(transportFactoryProvider)
  private val sessionCoordinator = SessionCoordinator(firebaseInstallations, eventGDTLogger)

  init {
    sessionGenerator =
      SessionGenerator(
        collectEvents = shouldCollectEvents(),
        timeProvider = WallClock,
      )
  }

  suspend fun initiateSessionStart(sessionDetails: SessionDetails) {
    val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()

    if (subscribers.isEmpty()) {
      Log.d(
        TAG,
        "Sessions SDK did not have any dependent SDKs register as dependencies. Events will not be sent."
      )
      return
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
        SessionEvents.startSession(
          firebaseApp,
          sessionDetails,
          sessionSettings,
          subscribers,
        )
      sessionCoordinator.attemptLoggingSessionEvent(sessionEvent)
    } catch (ex: IllegalStateException) {
      // This can happen if the app suddenly deletes the instance of FirebaseApp.
      Log.w(
        TAG,
        "FirebaseApp is not initialized. Sessions library will not collect session data.",
        ex,
      )
    }
  }

  /** Calculate whether we should sample events using settings data. */
  private fun shouldCollectEvents(): Boolean {
    // Sampling rate of 1 means the SDK will send every event.
    val randomValue = Math.random()
    return randomValue <= sessionSettings.samplingRate
  }

  internal companion object {
    private const val TAG = "SessionMaintainer"

    @JvmStatic
    val instance: SessionMaintainer
      get() = getInstance(Firebase.app)

    @JvmStatic
    fun getInstance(app: FirebaseApp): SessionMaintainer = app.get(SessionMaintainer::class.java)
  }
}
