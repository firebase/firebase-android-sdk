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

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * A delegate wrapper around OpenTelemetry. Use this to wrap OpenTelemetry to add Crashlytics'
 * functionality.
 *
 * @note
 * -
 * https://kotlinlang.org/docs/delegation.html#overriding-a-member-of-an-interface-implemented-by-delegation
 * indicates that if a method isn't implemented, Kotlin calls the delegate object by default.
 */
internal class CrashlyticsOpenTelemetryDelegate(
  private val delegate: OpenTelemetry,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : OpenTelemetry by delegate {
  override fun getTracer(
    instrumentationScopeName: String,
    instrumentationScopeVersion: String,
  ): Tracer {
    return CrashlyticsTracerDelegate(
      delegate.getTracer(instrumentationScopeName, instrumentationScopeVersion),
      crashlyticsSpanProcessor,
    )
  }
}
