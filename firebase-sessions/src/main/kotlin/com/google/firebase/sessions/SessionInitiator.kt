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

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * The [SessionInitiator] is responsible for calling the [initiateSessionStart] callback whenever a
 * session starts. This will happen at a cold start of the app, and when the app has been in the
 * background for a period of time (default 30 min) and then comes back to the foreground.
 *
 * @hide
 */
internal class SessionInitiator(
  private val currentTimeMs: () -> Long,
  private val initiateSessionStart: () -> Unit
) {
  private var backgroundTimeMs = currentTimeMs()
  private val sessionTimeoutMs = 30 * 60 * 1000L // TODO(mrober): Get session timeout from settings

  init {
    initiateSessionStart()
  }

  fun appBackgrounded() {
    backgroundTimeMs = currentTimeMs()
  }

  fun appForegrounded() {
    val intervalMs = currentTimeMs() - backgroundTimeMs
    if (intervalMs > sessionTimeoutMs) {
      initiateSessionStart()
    }
  }

  internal val activityLifecycleCallbacks =
    object : ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) = appForegrounded()

      override fun onActivityPaused(activity: Activity) = appBackgrounded()

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

      override fun onActivityStarted(activity: Activity) = Unit

      override fun onActivityStopped(activity: Activity) = Unit

      override fun onActivityDestroyed(activity: Activity) = Unit

      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
