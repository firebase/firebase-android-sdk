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
import com.google.firebase.sessions.settings.SessionsSettings
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The [SessionInitiator] is responsible for calling [SessionInitiateListener.onInitiateSession]
 * with a generated [SessionDetails] on the [backgroundDispatcher] whenever a new session initiates.
 * This will happen at a cold start of the app, and when the app has been in the background for a
 * period of time (default 30 min) and then comes back to the foreground.
 */
internal class SessionInitiator(
  private val timeProvider: TimeProvider,
  private val backgroundDispatcher: CoroutineContext,
  private val sessionInitiateListener: SessionInitiateListener,
  private val sessionsSettings: SessionsSettings,
  private val sessionGenerator: SessionGenerator,
) {
  private var backgroundTime = timeProvider.elapsedRealtime()

  init {
    initiateSession()
  }

  fun appBackgrounded() {
    backgroundTime = timeProvider.elapsedRealtime()
  }

  fun appForegrounded() {
    val interval = timeProvider.elapsedRealtime() - backgroundTime
    val sessionTimeout = sessionsSettings.sessionRestartTimeout
    if (interval > sessionTimeout) {
      initiateSession()
    }
  }

  private fun initiateSession() {
    // Generate the session details on main thread so the timestamp is as current as possible.
    val sessionDetails = sessionGenerator.generateNewSession()

    CoroutineScope(backgroundDispatcher).launch {
      sessionInitiateListener.onInitiateSession(sessionDetails)
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
