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

import android.util.Log
import androidx.datastore.core.DataStore
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.sessions.ProcessDetailsProvider.getProcessName
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch

@Singleton
internal class SharedSessionRepository
@Inject
constructor(
  private val sessionsSettings: SessionsSettings,
  private val sessionGenerator: SessionGenerator,
  private val sessionFirelogPublisher: SessionFirelogPublisher,
  private val timeProvider: TimeProvider,
  private val sessionDataStore: DataStore<SessionData>,
  @Background private val backgroundDispatcher: CoroutineContext,
) : SessionLifecycleClient {
  /** Local copy of the session data. Can get out of sync, must be double-checked in datastore. */
  override lateinit var localSessionData: SessionData

  private var jobForCancel: Job? = null

  init {
    jobForCancel =
      CoroutineScope(backgroundDispatcher).launch {
        sessionDataStore.data.cancellable().collect { sessionData ->
          localSessionData = sessionData
          val sessionId = sessionData.sessionDetails.sessionId

          FirebaseSessionsDependencies.getRegisteredSubscribers().values.forEach { subscriber ->
            // Notify subscribers, regardless of sampling and data collection state
            subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionId))
            Log.d(TAG, "Notified ${subscriber.sessionSubscriberName} of new session $sessionId")
          }
        }
      }
  }

  override fun appBackgrounded() {
    if (!::localSessionData.isInitialized) {
      Log.d(TAG, "App backgrounded, but local SessionData not initialized")
      return
    }
    val sessionData = localSessionData
    Log.d(TAG, "App backgrounded on ${getProcessName()} - $sessionData")

    CoroutineScope(backgroundDispatcher).launch {
      sessionDataStore.updateData {
        // TODO(mrober): Double check time makes sense?
        sessionData.copy(backgroundTime = timeProvider.currentTime())
      }
    }
  }

  override fun appForegrounded() {
    if (!::localSessionData.isInitialized) {
      Log.d(TAG, "App foregrounded, but local SessionData not initialized")
      return
    }
    val sessionData = localSessionData
    Log.d(TAG, "App foregrounded on ${getProcessName()} - $sessionData")

    if (shouldInitiateNewSession(sessionData)) {
      CoroutineScope(backgroundDispatcher).launch {
        sessionDataStore.updateData { currentSessionData ->
          // Double-check pattern
          if (shouldInitiateNewSession(currentSessionData)) {
            val newSessionDetails = sessionGenerator.generateNewSession(sessionData.sessionDetails)
            sessionFirelogPublisher.mayLogSession(sessionDetails = newSessionDetails)
            currentSessionData.copy(sessionDetails = newSessionDetails)
          } else {
            currentSessionData
          }
        }
      }
    }
  }

  override fun unregister() {
    jobForCancel?.cancel("Datastore turned off, stop flow")
  }

  private fun shouldInitiateNewSession(sessionData: SessionData): Boolean {
    val interval = timeProvider.currentTime() - sessionData.backgroundTime
    return interval > sessionsSettings.sessionRestartTimeout
  }

  private companion object {
    const val TAG = "SharedSessionRepository"
  }
}
