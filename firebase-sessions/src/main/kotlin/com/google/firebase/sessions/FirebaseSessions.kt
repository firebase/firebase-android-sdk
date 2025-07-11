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

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.app
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.settings.SessionsSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Responsible for initializing AQS */
@Singleton
internal class FirebaseSessions
@Inject
constructor(
  private val firebaseApp: FirebaseApp,
  private val settings: SessionsSettings,
  @Background backgroundDispatcher: CoroutineContext,
  sessionsActivityLifecycleCallbacks: SessionsActivityLifecycleCallbacks,
) {

  init {
    Log.d(TAG, "Initializing Firebase Sessions ${BuildConfig.VERSION_NAME}.")
    val appContext = firebaseApp.applicationContext.applicationContext
    if (appContext is Application) {
      appContext.registerActivityLifecycleCallbacks(sessionsActivityLifecycleCallbacks)

      CoroutineScope(backgroundDispatcher).launch {
        val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()
        if (subscribers.values.none { it.isDataCollectionEnabled }) {
          Log.d(TAG, "No Sessions subscribers. Not listening to lifecycle events.")
        } else {
          settings.updateSettings()
          if (!settings.sessionsEnabled) {
            Log.d(TAG, "Sessions SDK disabled. Not listening to lifecycle events.")
          } else {
            firebaseApp.addLifecycleEventListener { _, _ ->
              Log.w(
                TAG,
                "FirebaseApp instance deleted. Sessions library will stop collecting data.",
              )
              sessionsActivityLifecycleCallbacks.onAppDelete()
            }
          }
        }
      }
    } else {
      Log.e(
        TAG,
        "Failed to register lifecycle callbacks, unexpected context ${appContext.javaClass}.",
      )
    }
  }

  companion object {
    internal const val TAG = "FirebaseSessions"

    val instance: FirebaseSessions
      get() = Firebase.app[FirebaseSessions::class.java]
  }
}
