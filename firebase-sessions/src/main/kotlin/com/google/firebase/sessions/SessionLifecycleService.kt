/*
 * Copyright 2025 Google LLC
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

import android.app.Service
import android.content.Intent

/**
 * A no-op implementation of SessionLifecycleService.
 *
 * This service was previously used for session management but has been effectively made a no-op as
 * part of the architectural changes introduced in
 * [PR #7318](https://github.com/firebase/firebase-android-sdk/pull/7318). It is retained to prevent
 * `ClassNotFoundException` for older clients that might still have this service declared in their
 * AndroidManifest.xml or for systems attempting to restart a previously running instance of this
 * service.
 */
internal class SessionLifecycleService : Service() {

  override fun onBind(unused: Intent?) = null
}
