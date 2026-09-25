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
import com.google.firebase.app
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer

/** Main entry point for Firebase Crashlytics Telemetry SDK. */
public class FirebaseCrashlyticsTelemetry
internal constructor(private val openTelemetry: OpenTelemetry) {
  internal val eventEmitter: EventEmitter = EventEmitter(openTelemetry)

  /**
   * Records a screen navigation event.
   *
   * @param screenName The screen name.
   */
  public fun logScreenAppear(screenName: String) {
    eventEmitter.emit(
      TelemetryEvent.SCREEN_VIEW,
      Attributes.of(NAVIGATION_DESTINATION_NAME, screenName, SCREEN_NAME, screenName),
    )
  }

  /**
   * Records a screen navigation event.
   *
   * @param screenName The screen name.
   * @param screenClass The class name or route identifier.
   */
  public fun logScreenAppear(screenName: String, screenClass: String) {
    eventEmitter.emit(
      TelemetryEvent.SCREEN_VIEW,
      Attributes.of(
        NAVIGATION_DESTINATION_NAME,
        screenName,
        SCREEN_NAME,
        screenName,
        SCREEN_ID,
        screenClass,
      ),
    )
  }

  public fun getTracer(instrumentationScopeName: String): Tracer {
    return openTelemetry.getTracer(instrumentationScopeName)
  }

  public fun getTracer(
    instrumentationScopeName: String,
    instrumentationScopeVersion: String,
  ): Tracer {
    return openTelemetry.getTracer(instrumentationScopeName, instrumentationScopeVersion)
  }

  public companion object {
    private val NAVIGATION_DESTINATION_NAME: AttributeKey<String> =
      stringKey("app.navigation.destination.name")
    private val SCREEN_NAME: AttributeKey<String> = stringKey("app.screen.name")
    private val SCREEN_ID: AttributeKey<String> = stringKey("app.screen.id")

    /** The [FirebaseCrashlyticsTelemetry] instance for the default FirebaseApp. */
    @JvmStatic
    public val instance: FirebaseCrashlyticsTelemetry
      get() = Firebase.app[FirebaseCrashlyticsTelemetry::class.java]
  }
}

/** Access the FirebaseCrashlyticsTelemetry instance for the default FirebaseApp. */
public val Firebase.telemetry: FirebaseCrashlyticsTelemetry
  get() = FirebaseCrashlyticsTelemetry.instance

/** Access the FirebaseCrashlyticsTelemetry instance from FirebaseCrashlytics. */
public val FirebaseCrashlytics.telemetry: FirebaseCrashlyticsTelemetry
  get() = FirebaseCrashlyticsTelemetry.instance
