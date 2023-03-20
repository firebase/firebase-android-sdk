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
  backgroundDispatcher: CoroutineDispatcher
) {
  private val scope = CoroutineScope(backgroundDispatcher)

  fun attemptLoggingSessionEvent(sessionEvent: SessionEvent) =
    scope.launch {
      sessionEvent.sessionData.firebaseInstallationId =
        try {
          firebaseInstallations.id.await()
        } catch (ex: Exception) {
          Log.w(TAG, "Session Installations Error", ex)
          // Use an empty fid if there is any failure.
          ""
        }

      Log.i(TAG, "Initiate session start: $sessionEvent")
    }

  companion object {
    private const val TAG = "SessionCoordinator"
  }
}
