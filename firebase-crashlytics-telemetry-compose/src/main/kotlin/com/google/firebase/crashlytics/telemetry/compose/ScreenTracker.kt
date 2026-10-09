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
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.crashlytics.telemetry.FirebaseCrashlyticsTelemetry

/**
 * Tracks this composable as a screen for Firebase Crashlytics Telemetry when it enters the
 * composition. Use it for screens that are not Navigation destinations, such as dialogs and bottom
 * sheets.
 *
 * @param name The screen name.
 */
@Composable
public fun ScreenTracker(name: String) {
  LaunchedEffect(name) { FirebaseCrashlyticsTelemetry.instance.recordScreenAppear(name) }
}

/**
 * Tracks this composable as a screen for Firebase Crashlytics Telemetry when it enters the
 * composition. Use it for screens that are not Navigation destinations, such as dialogs and bottom
 * sheets.
 *
 * @param name The screen name.
 * @param screenClass The class name or route identifier.
 */
@Composable
public fun ScreenTracker(name: String, screenClass: String) {
  LaunchedEffect(name, screenClass) {
    FirebaseCrashlyticsTelemetry.instance.recordScreenAppear(name, screenClass)
  }
}
