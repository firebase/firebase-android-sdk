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
import io.opentelemetry.api.logs.LoggerProvider
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators

/**
 * A delegate wrapper around [OpenTelemetry] that wraps tracers in Crashlytics delegates.
 *
 * Most [OpenTelemetry] methods are Java `default` methods that return no-op providers, and Kotlin
 * `by` delegation does not forward them, so each one is overridden explicitly.
 */
internal class CrashlyticsOpenTelemetryDelegate(
  private val delegate: OpenTelemetry,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : OpenTelemetry {
  private val tracerProvider =
    CrashlyticsTracerProviderDelegate(delegate.tracerProvider, crashlyticsSpanProcessor)

  override fun getTracerProvider(): TracerProvider = tracerProvider

  override fun getLogsBridge(): LoggerProvider = delegate.logsBridge

  override fun getMeterProvider(): MeterProvider = delegate.meterProvider

  override fun getPropagators(): ContextPropagators = delegate.propagators
}
