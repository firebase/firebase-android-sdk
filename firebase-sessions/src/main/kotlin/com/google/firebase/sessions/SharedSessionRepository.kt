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
import com.google.firebase.sessions.FirebaseSessions.Companion.TAG
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Repository to persist session data to be shared between all app processes. */
internal interface SharedSessionRepository {
  fun appBackground()

  fun appForeground()
}

@Singleton
internal class SharedSessionRepositoryImpl
@Inject
constructor(
  private val sessionsSettings: SessionsSettings,
  private val sessionGenerator: SessionGenerator,
  private val sessionFirelogPublisher: SessionFirelogPublisher,
  private val timeProvider: TimeProvider,
  private val sessionDataStore: DataStore<SessionData>,
  private val processDataManager: ProcessDataManager,
  @Background private val backgroundDispatcher: CoroutineContext,
) : SharedSessionRepository {
  /** Local copy of the session data. Can get out of sync, must be double-checked in datastore. */
  internal lateinit var localSessionData: SessionData

  /**
   * Either notify the subscribers with general multi-process supported session or fallback local
   * session
   */
  internal enum class NotificationType {
    GENERAL,
    FALLBACK,
  }

  internal var previousNotificationType: NotificationType = NotificationType.GENERAL
  private var previousSessionId: String = ""

  init {
    CoroutineScope(backgroundDispatcher).launch {
      sessionDataStore.data
        .catch {
          val newSession =
            SessionData(
              sessionDetails = sessionGenerator.generateNewSession(null),
              backgroundTime = null,
            )
          Log.d(
            TAG,
            "Init session datastore failed with exception message: ${it.message}. Emit fallback session ${newSession.sessionDetails.sessionId}",
          )
          emit(newSession)
        }
        .collect { sessionData ->
          localSessionData = sessionData
          val sessionId = sessionData.sessionDetails.sessionId
          notifySubscribers(sessionId, NotificationType.GENERAL)
        }
    }
  }

  override fun appBackground() {
    if (!::localSessionData.isInitialized) {
      Log.d(TAG, "App backgrounded, but local SessionData not initialized")
      return
    }
    val sessionData = localSessionData
    Log.d(TAG, "App backgrounded on ${processDataManager.myProcessName} - $sessionData")

    CoroutineScope(backgroundDispatcher).launch {
      try {
        sessionDataStore.updateData {
          // TODO(mrober): Double check time makes sense?
          sessionData.copy(backgroundTime = timeProvider.currentTime())
        }
      } catch (ex: Exception) {
        Log.d(TAG, "App backgrounded, failed to update data. Message: ${ex.message}")
        localSessionData = localSessionData.copy(backgroundTime = timeProvider.currentTime())
      }
    }
  }

  override fun appForeground() {
    if (!::localSessionData.isInitialized) {
      Log.d(TAG, "App foregrounded, but local SessionData not initialized")
      return
    }
    val sessionData = localSessionData
    Log.d(TAG, "App foregrounded on ${processDataManager.myProcessName} - $sessionData")

    // Check if maybe the session data needs to be updated
    if (isSessionExpired(sessionData) || isMyProcessStale(sessionData)) {
      CoroutineScope(backgroundDispatcher).launch {
        try {
          sessionDataStore.updateData { currentSessionData ->
            // Check again using the current session data on disk
            val isSessionExpired = isSessionExpired(currentSessionData)
            val isColdStart = isColdStart(currentSessionData)
            val isMyProcessStale = isMyProcessStale(currentSessionData)

            val newProcessDataMap =
              if (isColdStart) {
                // Generate a new process data map for cold app start
                processDataManager.generateProcessDataMap()
              } else if (isMyProcessStale) {
                // Update the data map with this process if stale
                processDataManager.updateProcessDataMap(currentSessionData.processDataMap)
              } else {
                // No change
                currentSessionData.processDataMap
              }

            // This is an expression, and returns the updated session data
            if (isSessionExpired || isColdStart) {
              val newSessionDetails =
                sessionGenerator.generateNewSession(currentSessionData.sessionDetails)
              sessionFirelogPublisher.mayLogSession(sessionDetails = newSessionDetails)
              processDataManager.onSessionGenerated()
              currentSessionData.copy(
                sessionDetails = newSessionDetails,
                backgroundTime = null,
                processDataMap = newProcessDataMap,
              )
            } else if (isMyProcessStale) {
              currentSessionData.copy(
                processDataMap = processDataManager.updateProcessDataMap(newProcessDataMap)
              )
            } else {
              currentSessionData
            }
          }
        } catch (ex: Exception) {
          Log.d(TAG, "App appForegrounded, failed to update data. Message: ${ex.message}")
          if (isSessionExpired(sessionData)) {
            val newSessionDetails = sessionGenerator.generateNewSession(sessionData.sessionDetails)
            localSessionData =
              sessionData.copy(sessionDetails = newSessionDetails, backgroundTime = null)
            sessionFirelogPublisher.mayLogSession(sessionDetails = newSessionDetails)
            notifySubscribers(newSessionDetails.sessionId, NotificationType.FALLBACK)
          }
        }
      }
    }
  }

  private suspend fun notifySubscribers(sessionId: String, type: NotificationType) {
    previousNotificationType = type
    if (previousSessionId == sessionId) {
      return
    }
    previousSessionId = sessionId
    FirebaseSessionsDependencies.getRegisteredSubscribers().values.forEach { subscriber ->
      // Notify subscribers, regardless of sampling and data collection state
      subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionId))
      Log.d(
        TAG,
        when (type) {
          NotificationType.GENERAL ->
            "Notified ${subscriber.sessionSubscriberName} of new session $sessionId"
          NotificationType.FALLBACK ->
            "Notified ${subscriber.sessionSubscriberName} of new fallback session $sessionId"
        },
      )
    }
  }

  /** Checks if the session has expired. If no background time, consider it not expired. */
  private fun isSessionExpired(sessionData: SessionData): Boolean {
    sessionData.backgroundTime?.let { backgroundTime ->
      val interval = timeProvider.currentTime() - backgroundTime
      val sessionExpired = (interval > sessionsSettings.sessionRestartTimeout)
      if (sessionExpired) {
        Log.d(TAG, "Session ${sessionData.sessionDetails.sessionId} is expired")
      }
      return sessionExpired
    }

    Log.d(TAG, "Session ${sessionData.sessionDetails.sessionId} has not backgrounded yet")
    return false
  }

  /** Checks for cold app start. If no process data map, consider it a cold start. */
  private fun isColdStart(sessionData: SessionData): Boolean {
    sessionData.processDataMap?.let { processDataMap ->
      val coldStart = processDataManager.isColdStart(processDataMap)
      if (coldStart) {
        Log.d(TAG, "Cold app start detected")
      }
      return coldStart
    }

    Log.d(TAG, "No process data map")
    return true
  }

  /** Checks if this process is stale. If no process data map, consider the process stale. */
  private fun isMyProcessStale(sessionData: SessionData): Boolean {
    sessionData.processDataMap?.let { processDataMap ->
      val myProcessStale = processDataManager.isMyProcessStale(processDataMap)
      if (myProcessStale) {
        Log.d(TAG, "Process ${processDataManager.myProcessName} is stale")
      }
      return myProcessStale
    }

    Log.d(TAG, "No process data for ${processDataManager.myProcessName}")
    return true
  }
}
