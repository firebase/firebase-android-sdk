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

internal interface SessionLifecycleClient {
  var localSessionData: SessionData
  fun appForegrounded()
  fun appBackgrounded()
  fun unregister() = Unit
}

internal object SessionInitiator : ActivityLifecycleCallbacks {
  var currentLocalSession: SessionDetails? = null
    get() {
      return lifecycleClient?.localSessionData?.sessionDetails
    }
  var lifecycleClient: SessionLifecycleClient? = null
    set(lifecycleClient) {
      field = lifecycleClient
    }

  override fun onActivityResumed(activity: Activity) {
    lifecycleClient?.appForegrounded()
  }

  override fun onActivityPaused(activity: Activity) {
    lifecycleClient?.appBackgrounded()
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
