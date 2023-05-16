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
import com.google.firebase.installations.FirebaseInstallationsApi
import kotlinx.coroutines.tasks.await

/**
 * [SessionCoordinator] is responsible for coordinating the systems in this SDK involved with
 * sending a [SessionEvent].
 *
 * @hide
 */
internal class SessionCoordinator(
  private val firebaseInstallations: FirebaseInstallationsApi,
  private val eventGDTLogger: EventGDTLoggerInterface,
) {
  suspend fun attemptLoggingSessionEvent(sessionEvent: SessionEvent) {
    sessionEvent.sessionData.firebaseInstallationId =
      try {
        firebaseInstallations.id.await()
      } catch (ex: Exception) {
        Log.e(TAG, "Error getting Firebase Installation ID: ${ex}. Using an empty ID")
        // Use an empty fid if there is any failure.
        ""
      }

    try {
      eventGDTLogger.log(sessionEvent)

      Log.i(TAG, "Successfully logged Session Start event: ${sessionEvent.sessionData.sessionId}")
    } catch (ex: RuntimeException) {
      Log.e(TAG, "Error logging Session Start event to DataTransport: ", ex)
    }
  }

  companion object {
    private const val TAG = "SessionCoordinator"
  }
}
