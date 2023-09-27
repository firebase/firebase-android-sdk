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

package com.google.firebase.sessions.follower

import android.util.Log
import com.google.firebase.sessions.SessionMaintainer
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SessionMaintainerFollower(private val sessionsDataRepository: SessionsDataRepository) :
  SessionMaintainer {
  val tag = "SessionMaintainerFollow"

  override fun register(subscriber: SessionSubscriber) {
    // NOOP
  }

  override fun start(backgroundDispatcher: CoroutineDispatcher) {
    CoroutineScope(backgroundDispatcher).launch {
      sessionsDataRepository.firebaseSessionDataFlow.collect {
        if (it.sessionId == null) {
          Log.d(
            tag,
            "No session data available in shared storage." + " subscribers will not be notified."
          )
        } else {
          Log.d(
            tag,
            "Follower process has observed a change to the repository and will notify subscribers. New session id is ${it.sessionId}"
          )
          notifySubscribers(it.sessionId)
        }
      }
    }
  }

  private suspend fun notifySubscribers(sessionId: String) {
    val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()
    if (subscribers.isEmpty()) {
      Log.d(tag, "Sessions SDK did not have any subscribers. Events will not be sent.")
    } else {
      subscribers.values.forEach { subscriber ->
        // Notify subscribers, irregardless ;) of sampling and data collection state.
        Log.d(tag, "Sending session id $sessionId to subscriber $subscriber.")
        subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionId))
      }
    }
  }
}
