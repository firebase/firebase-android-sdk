/*
 * Copyright 2024 Google LLC
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

/** Provides the Firebase installation id and Firebase authentication token. */
internal class InstallationId private constructor(val fid: String, val authToken: String) {
  companion object {
    private const val TAG = "FirebaseInstallationId"

    suspend fun create(firebaseInstallations: FirebaseInstallationsApi) =
      try {
        // Fetch the auth token first, so the fid will be validated.
        val authToken = firebaseInstallations.getToken(false).await().token
        val fid = firebaseInstallations.id.await()
        InstallationId(fid, authToken)
      } catch (ex: Exception) {
        Log.e(TAG, "Error getting Firebase installation id or authentication token.", ex)
        // If there are any failures, the fid is not validated. So return empty values for both.
        InstallationId(fid = "", authToken = "")
      }
  }
}
