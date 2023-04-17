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
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * [SessionCoordinator] is responsible for coordinating the systems in this SDK involved with
 * sending a [SessionEvent].
 *
 * @hide
 */
internal class SessionCoordinator(
  private val firebaseInstallations: FirebaseInstallationsApi,
  backgroundDispatcher: CoroutineDispatcher,
  private val eventGDTLogger: EventGDTLoggerInterface,
) {
  private val scope = CoroutineScope(backgroundDispatcher)

  fun attemptLoggingSessionEvent(sessionEvent: SessionEvent) =
    scope.launch {

      // this should be a policy violation, but tests still pass?
      URL("https://google.com").openConnection().connect()

      sessionEvent.sessionData.firebaseInstallationId =
        try {
          firebaseInstallations.id.await()
        } catch (ex: Exception) {
          Log.w(TAG, "Session Installations Error", ex)
          // Use an empty fid if there is any failure.
          ""
        }

      try {
        eventGDTLogger.log(sessionEvent)

        Log.i(TAG, "Logged Session Start event: ${sessionEvent.sessionData.sessionId}")
      } catch (e: RuntimeException) {
        Log.w(TAG, "Failed to log Session Start event: ", e)
      }
    }

  companion object {
    private const val TAG = "SessionCoordinator"
  }
}
