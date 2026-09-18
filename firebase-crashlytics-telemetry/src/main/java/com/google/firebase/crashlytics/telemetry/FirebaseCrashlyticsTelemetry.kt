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

package com.google.firebase.crashlytics.telemetry

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * Main entry point for Firebase Crashlytics Telemetry SDK.
 */
class FirebaseCrashlyticsTelemetry internal constructor(
  val openTelemetry: OpenTelemetry,
) {
  fun getTracer(instrumentationScopeName: String): Tracer {
    return openTelemetry.getTracer(instrumentationScopeName)
  }

  fun getTracer(instrumentationScopeName: String, instrumentationScopeVersion: String): Tracer {
    return openTelemetry.getTracer(instrumentationScopeName, instrumentationScopeVersion)
  }

  companion object {
    @JvmStatic
    fun getInstance(): FirebaseCrashlyticsTelemetry {
      return FirebaseCrashlyticsTelemetry(OpenTelemetryManager.instance)
    }

    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseCrashlyticsTelemetry {
      return FirebaseCrashlyticsTelemetry(OpenTelemetryManager.instance)
    }
  }
}

/**
 * Access the FirebaseCrashlyticsTelemetry instance for the default FirebaseApp.
 */
val Firebase.telemetry: FirebaseCrashlyticsTelemetry
  get() = FirebaseCrashlyticsTelemetry.getInstance()

/**
 * Access the FirebaseCrashlyticsTelemetry instance from FirebaseCrashlytics.
 */
val FirebaseCrashlytics.telemetry: FirebaseCrashlyticsTelemetry
  get() = FirebaseCrashlyticsTelemetry.getInstance()

/**
 * Access the FirebaseCrashlyticsTelemetry instance for a specific FirebaseApp.
 */
fun Firebase.telemetry(app: FirebaseApp): FirebaseCrashlyticsTelemetry =
  FirebaseCrashlyticsTelemetry.getInstance(app)
