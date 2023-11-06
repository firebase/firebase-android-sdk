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
import androidx.annotation.VisibleForTesting

/**
 * Lifecycle callbacks that will inform the [SessionLifecycleClient] whenever an [Activity] in this
 * application process goes foreground or background.
 */
internal object SessionsActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
  @VisibleForTesting internal var hasPendingForeground: Boolean = false

  var lifecycleClient: SessionLifecycleClient? = null
    /** Sets the client and calls [SessionLifecycleClient.foregrounded] for pending foreground. */
    set(lifecycleClient) {
      field = lifecycleClient
      lifecycleClient?.let {
        if (hasPendingForeground) {
          hasPendingForeground = false
          it.foregrounded()
        }
      }
    }

  override fun onActivityResumed(activity: Activity) {
    lifecycleClient?.foregrounded() ?: run { hasPendingForeground = true }
  }

  override fun onActivityPaused(activity: Activity) {
    lifecycleClient?.backgrounded()
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
