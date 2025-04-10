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

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.util.Log
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * This is the fallback module for datastore implementation (SharedSessionRepository). We are
 * fallback to pre multi-process support behavior.
 */
@Singleton
internal class SessionsFallbackActivityLifecycleCallbacks
@Inject
constructor(
  private val sessionsSettings: SessionsSettings,
  private val sessionGenerator: SessionGenerator,
  private val timeProvider: TimeProvider,
  @Background private val backgroundDispatcher: CoroutineContext,
) {
  private var localSessionData: SessionData
  init {
    localSessionData =
      SessionData(
        sessionDetails = sessionGenerator.generateNewSession(null),
        backgroundTime = timeProvider.currentTime()
      )
    val sessionId = localSessionData.sessionDetails.sessionId
    notifySubscribers(sessionId)
  }

  fun appBackground() {
    localSessionData.copy(backgroundTime = timeProvider.currentTime())
  }

  fun appForeground() {
    if (shouldInitiateNewSession(localSessionData)) {
      val newSessionDetails = sessionGenerator.generateNewSession(localSessionData.sessionDetails)
      localSessionData.copy(sessionDetails = newSessionDetails)
      notifySubscribers(localSessionData.sessionDetails.sessionId)
    }
  }

  internal val activityLifecycleCallbacks =
    object : ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) = appForeground()

      override fun onActivityPaused(activity: Activity) = appBackground()

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

      override fun onActivityStarted(activity: Activity) = Unit

      override fun onActivityStopped(activity: Activity) = Unit

      override fun onActivityDestroyed(activity: Activity) = Unit

      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

  private fun notifySubscribers(sessionId: String) {
    CoroutineScope(backgroundDispatcher).launch {
      // Only notify subscriber for session change, not send to event to firelog
      FirebaseSessionsDependencies.getRegisteredSubscribers().values.forEach { subscriber ->
        subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionId))
        Log.d(
          TAG,
          "Notified ${subscriber.sessionSubscriberName} of new session $sessionId with fallback mode"
        )
      }
    }
  }

  private fun shouldInitiateNewSession(sessionData: SessionData): Boolean {
    val interval = timeProvider.currentTime() - sessionData.backgroundTime
    return interval > sessionsSettings.sessionRestartTimeout
  }

  private companion object {
    const val TAG = "SessionsFallbackALC"
  }
}
