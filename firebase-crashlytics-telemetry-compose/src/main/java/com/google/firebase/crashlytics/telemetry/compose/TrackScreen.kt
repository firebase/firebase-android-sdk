/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.telemetry.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tracks this composable as a screen for Firebase Crashlytics Telemetry when its enclosing
 * lifecycle resumes. Use it for screens that are not Navigation destinations, such as dialogs and
 * bottom sheets.
 *
 * @param name The screen name.
 */
@Composable
public fun TrackScreen(name: String) {
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, name) {
    val observer = ScreenLifecycleObserver(name, DefaultScreenReporter)
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

/**
 * Tracks this composable as a screen for Firebase Crashlytics Telemetry when its enclosing
 * lifecycle resumes. Use it for screens that are not Navigation destinations, such as dialogs and
 * bottom sheets.
 *
 * @param name The screen name.
 * @param screenClass The class name or route identifier.
 */
@Composable
public fun TrackScreen(name: String, screenClass: String) {
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, name, screenClass) {
    val observer = ScreenLifecycleObserver(name, screenClass, DefaultScreenReporter)
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

internal class ScreenLifecycleObserver private constructor(private val onResume: () -> Unit) :
  LifecycleEventObserver {
  constructor(name: String, reporter: ScreenReporter) : this({ reporter.appear(name) })

  constructor(
    name: String,
    screenClass: String,
    reporter: ScreenReporter,
  ) : this({ reporter.appear(name, screenClass) })

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    if (event == Lifecycle.Event.ON_RESUME) {
      onResume()
    }
  }
}
