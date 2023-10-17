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
import kotlinx.coroutines.tasks.await

/**
 * [SessionFirelogPublisher] is responsible for publishing sessions to firelog
 *
 * @hide
 */
internal class SessionFirelogPublisher(
  private val firebaseApp: FirebaseApp,
  private val firebaseInstallations: FirebaseInstallationsApi,
  private val sessionSettings: SessionsSettings,
  private val eventGDTLogger: EventGDTLoggerInterface,
  private val backgroundDispatcher: CoroutineContext,
) {

  /**
   * Logs the session represented by the given [SessionDetails] to Firelog on a background thread.
   *
   * This will pull all the necessary information about the device in order to create a full
   * [SessionEvent], and then upload that through the Firelog interface.
   */
  fun logSession(sessionDetails: SessionDetails) {
    CoroutineScope(backgroundDispatcher).launch {
      val sessionEvent =
        SessionEvents.buildSession(
          firebaseApp,
          sessionDetails,
          sessionSettings,
          FirebaseSessionsDependencies.getRegisteredSubscribers(),
        )
      sessionEvent.sessionData.firebaseInstallationId = getFid()
      attemptLoggingSessionEvent(sessionEvent)
    }
  }

  /** Attempts to write the given [SessionEvent] to firelog. Failures are logged and ignored. */
  private suspend fun attemptLoggingSessionEvent(sessionEvent: SessionEvent) {
    try {
      eventGDTLogger.log(sessionEvent)
      Log.i(TAG, "Successfully logged Session Start event: ${sessionEvent.sessionData.sessionId}")
    } catch (ex: RuntimeException) {
      Log.e(TAG, "Error logging Session Start event to DataTransport: ", ex)
    }
  }

  /** Gets the Firebase Installation ID for the current app installation. */
  private suspend fun getFid() =
    try {
      firebaseInstallations.id.await()
    } catch (ex: Exception) {
      Log.e(TAG, "Error getting Firebase Installation ID: ${ex}. Using an empty ID")
      // Use an empty fid if there is any failure.
      ""
    }

  internal companion object {
    const val TAG = "SessionFirelogPublisher"

    fun getInstance(app: FirebaseApp): SessionFirelogPublisher =
      app.get(SessionFirelogPublisher::class.java)

    val instance: SessionFirelogPublisher
      get() = getInstance(Firebase.app)
  }
}
