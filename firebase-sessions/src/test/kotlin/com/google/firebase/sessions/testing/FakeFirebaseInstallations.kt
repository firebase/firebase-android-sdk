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

package com.google.firebase.sessions.testing

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.installations.InstallationTokenResult
import com.google.firebase.installations.internal.FidListener
import com.google.firebase.installations.internal.FidListenerHandle

/** Fake [FirebaseInstallationsApi] that implements [getId] and always returns the given [fid]. */
internal class FakeFirebaseInstallations(private val fid: String = "") : FirebaseInstallationsApi {
  override fun getId(): Task<String> = Tasks.forResult(fid)

  override fun getToken(forceRefresh: Boolean): Task<InstallationTokenResult> =
    throw NotImplementedError("getToken not faked.")

  override fun delete(): Task<Void> = throw NotImplementedError("delete not faked.")

  override fun registerFidListener(listener: FidListener): FidListenerHandle =
    throw NotImplementedError("registerFidListener not faked.")
}
