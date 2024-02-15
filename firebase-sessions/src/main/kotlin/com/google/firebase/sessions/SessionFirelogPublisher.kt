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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.settings.SessionsSettings
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Responsible for uploading session events to Firelog. */
internal fun interface SessionFirelogPublisher {

  /** Asynchronously logs the session represented by the given [SessionDetails] to Firelog. */
  fun logSession(sessionDetails: SessionDetails)

  companion object {
    val instance: SessionFirelogPublisher
      get() = Firebase.app[SessionFirelogPublisher::class.java]
  }
}

/**
 * [SessionFirelogPublisher] is responsible for publishing sessions to firelog
 *
 * @hide
 */
internal class SessionFirelogPublisherImpl(
  private val firebaseApp: FirebaseApp,
  private val firebaseInstallations: FirebaseInstallationsApi,
  private val sessionSettings: SessionsSettings,
  private val eventGDTLogger: EventGDTLoggerInterface,
  private val backgroundDispatcher: CoroutineContext,
) : SessionFirelogPublisher {

  /**
   * Logs the session represented by the given [SessionDetails] to Firelog on a background thread.
   *
   * This will pull all the necessary information about the device in order to create a full
   * [SessionEvent], and then upload that through the Firelog interface.
   */
  override fun logSession(sessionDetails: SessionDetails) {
    CoroutineScope(backgroundDispatcher).launch {
      if (shouldLogSession()) {
        val installationId = InstallationId.create(firebaseInstallations)
        attemptLoggingSessionEvent(
          SessionEvents.buildSession(
            firebaseApp,
            sessionDetails,
            sessionSettings,
            FirebaseSessionsDependencies.getRegisteredSubscribers(),
            firebaseInstallationId = installationId.fid,
            firebaseAuthenticationToken = installationId.authToken
          )
        )
      }
    }
  }

  /** Attempts to write the given [SessionEvent] to firelog. Failures are logged and ignored. */
  private fun attemptLoggingSessionEvent(sessionEvent: SessionEvent) {
    try {
      eventGDTLogger.log(sessionEvent)
      Log.d(TAG, "Successfully logged Session Start event: ${sessionEvent.sessionData.sessionId}")
    } catch (ex: RuntimeException) {
      Log.e(TAG, "Error logging Session Start event to DataTransport: ", ex)
    }
  }

  /** Determines if the SDK should log a session to Firelog. */
  private suspend fun shouldLogSession(): Boolean {
    Log.d(TAG, "Data Collection is enabled for at least one Subscriber")

    // This will cause remote settings to be fetched if the cache is expired.
    sessionSettings.updateSettings()

    if (!sessionSettings.sessionsEnabled) {
      Log.d(TAG, "Sessions SDK disabled. Events will not be sent.")
      return false
    }

    if (!shouldCollectEvents()) {
      Log.d(TAG, "Sessions SDK has dropped this session due to sampling.")
      return false
    }

    return true
  }

  /** Calculate whether we should sample events using [SessionsSettings] data. */
  private fun shouldCollectEvents(): Boolean {
    // Sampling rate of 1 means the SDK will send every event.
    return randomValueForSampling <= sessionSettings.samplingRate
  }

  internal companion object {
    private const val TAG = "SessionFirelogPublisher"

    private val randomValueForSampling: Double = Math.random()
  }
}
