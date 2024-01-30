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

package com.google.firebase.sessions.api

import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.Collections.synchronizedMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [FirebaseSessionsDependencies] determines when a dependent SDK is installed in the app. The
 * Sessions SDK uses this to figure out which dependencies to wait for to getting the data
 * collection state. This is thread safe.
 *
 * This is important because the Sessions SDK starts up before dependent SDKs.
 */
object FirebaseSessionsDependencies {
  private const val TAG = "SessionsDependencies"

  private val dependencies = synchronizedMap(mutableMapOf<SessionSubscriber.Name, Dependency>())

  /**
   * Add a subscriber as a dependency to the Sessions SDK. Every dependency must register itself, or
   * the Sessions SDK will never generate a session.
   */
  @JvmStatic
  fun addDependency(subscriberName: SessionSubscriber.Name) {
    if (subscriberName == SessionSubscriber.Name.PERFORMANCE) {
      throw IllegalArgumentException(
        """
          Incompatible versions of Firebase Perf and Firebase Sessions.
          A safe combination would be:
            firebase-sessions:1.1.0
            firebase-crashlytics:18.5.0
            firebase-perf:20.5.0
          For more information contact Firebase Support.
        """
          .trimIndent()
      )
    }
    if (dependencies.containsKey(subscriberName)) {
      Log.d(TAG, "Dependency $subscriberName already added.")
      return
    }

    // The dependency is locked until the subscriber registers itself.
    dependencies[subscriberName] = Dependency(Mutex(locked = true))
    Log.d(TAG, "Dependency to $subscriberName added.")
  }

  /**
   * Register and unlock the subscriber. This must be called before [getRegisteredSubscribers] can
   * return.
   */
  @JvmStatic
  fun register(subscriber: SessionSubscriber) {
    val subscriberName = subscriber.sessionSubscriberName
    val dependency = getDependency(subscriberName)

    dependency.subscriber?.run {
      Log.d(TAG, "Subscriber $subscriberName already registered.")
      return
    }
    dependency.subscriber = subscriber
    Log.d(TAG, "Subscriber $subscriberName registered.")

    // Unlock to show the subscriber has been registered, it is possible to get it now.
    dependency.mutex.unlock()
  }

  /** Gets the subscribers safely, blocks until all the subscribers are registered. */
  internal suspend fun getRegisteredSubscribers(): Map<SessionSubscriber.Name, SessionSubscriber> {
    // The call to getSubscriber will never throw because the mutex guarantees it's been registered.
    return dependencies.mapValues { (subscriberName, dependency) ->
      dependency.mutex.withLock { getSubscriber(subscriberName) }
    }
  }

  /** Gets the subscriber, regardless of being registered. This is exposed for testing. */
  @VisibleForTesting
  internal fun getSubscriber(subscriberName: SessionSubscriber.Name): SessionSubscriber {
    return getDependency(subscriberName).subscriber
      ?: throw IllegalStateException("Subscriber $subscriberName has not been registered.")
  }

  /** Resets all the dependencies for testing purposes. */
  @VisibleForTesting internal fun reset() = dependencies.clear()

  private fun getDependency(subscriberName: SessionSubscriber.Name): Dependency {
    return dependencies.getOrElse(subscriberName) {
      throw IllegalStateException(
        "Cannot get dependency $subscriberName. Dependencies should be added at class load time."
      )
    }
  }

  private data class Dependency(val mutex: Mutex, var subscriber: SessionSubscriber? = null)
}
