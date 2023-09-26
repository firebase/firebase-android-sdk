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

import android.util.Log
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.leader.SessionMaintainerLeader
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The [FirebaseSessions] API provides methods to register a [SessionSubscriber] and delegates to a
 * [SessionMaintainer].
 */
class FirebaseSessions
internal constructor(
  firebaseApp: FirebaseApp,
  firebaseInstallations: FirebaseInstallationsApi,
  backgroundDispatcher: CoroutineDispatcher,
  blockingDispatcher: CoroutineDispatcher,
  transportFactoryProvider: Provider<TransportFactory>,
) {

  private val sessionMaintainer: SessionMaintainer
  private val tag = "FirebaseSessions"

  init {
    // TODO(rothbutter): create a different maintainer based on the characteristics of this process
    sessionMaintainer =
      SessionMaintainerLeader(
        firebaseApp,
        firebaseInstallations,
        backgroundDispatcher,
        blockingDispatcher,
        transportFactoryProvider
      )

    sessionMaintainer.start(backgroundDispatcher)
  }

  /** Register the [subscriber]. This must be called for every dependency. */
  fun register(subscriber: SessionSubscriber) {
    FirebaseSessionsDependencies.register(subscriber)

    Log.d(
      tag,
      "Registering Sessions SDK subscriber with name: ${subscriber.sessionSubscriberName}, " +
        "data collection enabled: ${subscriber.isDataCollectionEnabled}"
    )
    sessionMaintainer.register(subscriber)
  }

  companion object {
    private const val TAG = "SessionMaintainerLeader"

    @JvmStatic
    val instance: FirebaseSessions
      get() = getInstance(Firebase.app)

    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseSessions = app.get(FirebaseSessions::class.java)
  }
}
