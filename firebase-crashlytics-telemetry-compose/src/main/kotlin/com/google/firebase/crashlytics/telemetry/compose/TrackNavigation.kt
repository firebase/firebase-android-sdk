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
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.google.firebase.crashlytics.telemetry.FirebaseCrashlyticsTelemetry

/**
 * Tracks every destination this Jetpack Navigation controller navigates to as a screen for Firebase
 * Crashlytics Telemetry, for both forward and back navigation.
 *
 * @return this controller, for chaining.
 */
@Composable
public fun NavHostController.trackNavigation(): NavHostController {
  DisposableEffect(this) {
    val listener =
      NavController.OnDestinationChangedListener { _, destination, _ ->
        val name = screenName(destination.route, destination.displayName)
        FirebaseCrashlyticsTelemetry.instance.recordScreenAppear(name)
      }
    addOnDestinationChangedListener(listener)
    onDispose { removeOnDestinationChangedListener(listener) }
  }
  return this
}

/**
 * Names a destination by its route pattern, such as `profile/{userId}`, so argument values are
 * never recorded.
 */
internal fun screenName(route: String?, displayName: String): String = route ?: displayName
