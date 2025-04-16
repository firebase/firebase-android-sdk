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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle callbacks that will inform the [SharedSessionRepository] whenever an [Activity] in this
 * application process goes foreground or background.
 */
@Singleton
internal class SessionsActivityLifecycleCallbacks
@Inject
constructor(private val sharedSessionRepository: SharedSessionRepository) :
  ActivityLifecycleCallbacks {
  private var enabled = true

  fun onAppDelete() {
    enabled = false
  }

  override fun onActivityResumed(activity: Activity) {
    if (enabled) {
      sharedSessionRepository.appForeground()
    }
  }

  override fun onActivityPaused(activity: Activity) {
    if (enabled) {
      sharedSessionRepository.appBackground()
    }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
