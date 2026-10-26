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

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider

/** Wraps every [Tracer] returned by [delegate] in a [CrashlyticsTracerDelegate]. */
internal class CrashlyticsTracerProviderDelegate(
  private val delegate: TracerProvider,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : TracerProvider {
  override fun get(instrumentationScopeName: String): Tracer =
    CrashlyticsTracerDelegate(delegate.get(instrumentationScopeName), crashlyticsSpanProcessor)

  override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): Tracer =
    CrashlyticsTracerDelegate(
      delegate.get(instrumentationScopeName, instrumentationScopeVersion),
      crashlyticsSpanProcessor,
    )

  override fun tracerBuilder(instrumentationScopeName: String): TracerBuilder =
    CrashlyticsTracerBuilderDelegate(
      delegate.tracerBuilder(instrumentationScopeName),
      crashlyticsSpanProcessor,
    )
}

private class CrashlyticsTracerBuilderDelegate(
  private val delegate: TracerBuilder,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : TracerBuilder {
  override fun setSchemaUrl(schemaUrl: String): TracerBuilder {
    delegate.setSchemaUrl(schemaUrl)
    return this
  }

  override fun setInstrumentationVersion(instrumentationScopeVersion: String): TracerBuilder {
    delegate.setInstrumentationVersion(instrumentationScopeVersion)
    return this
  }

  override fun build(): Tracer =
    CrashlyticsTracerDelegate(delegate.build(), crashlyticsSpanProcessor)
}
