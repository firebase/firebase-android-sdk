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
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber

/** The [FirebaseSessions] API provides methods to register a [SessionSubscriber]. */
class FirebaseSessions internal constructor(private val firebaseApp: FirebaseApp) {

  init {
    val appContext = firebaseApp.applicationContext.applicationContext
    if (appContext is Application) {
      SessionLifecycleClient.bindToService(appContext)
      appContext.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)

      firebaseApp.addLifecycleEventListener { _, _ ->
        Log.w(TAG, "FirebaseApp instance deleted. Sessions library will not collect session data.")
        appContext.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
      }
    } else {
      Log.e(
        TAG,
        "Failed to register lifecycle callbacks, unexpected context ${appContext.javaClass}."
      )
    }
  }

  /** Register the [subscriber]. This must be called for every dependency. */
  fun register(subscriber: SessionSubscriber) {
    FirebaseSessionsDependencies.register(subscriber)

    Log.d(
      TAG,
      "Registering Sessions SDK subscriber with name: ${subscriber.sessionSubscriberName}, " +
        "data collection enabled: ${subscriber.isDataCollectionEnabled}"
    )
  }

  /** Calculate whether we should sample events using [sessionSettings] data. */
  companion object {
    private const val TAG = "FirebaseSessions"

    @JvmStatic
    val instance: FirebaseSessions
      get() = Firebase.app.get(FirebaseSessions::class.java)

    @JvmStatic
    @Deprecated(
      "Firebase Sessions only supports the Firebase default app.",
      ReplaceWith("FirebaseSessions.instance"),
    )
    fun getInstance(app: FirebaseApp): FirebaseSessions =
      if (app == Firebase.app) {
        app.get(FirebaseSessions::class.java)
      } else {
        throw IllegalArgumentException("Firebase Sessions only supports the Firebase default app.")
      }

    /**
     * Lifecycle callbacks that will inform the [SessionLifecycleClient] whenever an [Activity] in
     * this application process goes foreground or background.
     */
    private val activityLifecycleCallbacks =
      object : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = SessionLifecycleClient.foregrounded()

        override fun onActivityPaused(activity: Activity) = SessionLifecycleClient.backgrounded()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      }
  }
}
