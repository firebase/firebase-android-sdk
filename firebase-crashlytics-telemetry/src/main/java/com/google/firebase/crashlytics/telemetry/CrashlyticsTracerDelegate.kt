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

import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

/** A delegate wrapper around a Tracer. An interim delegate to add a delegate to SpanBuilder. */
class CrashlyticsTracerDelegate(
  private val delegate: Tracer,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : Tracer by delegate {
  override fun spanBuilder(spanName: String): SpanBuilder {
    return CrashlyticsSpanBuilderDelegate(delegate.spanBuilder(spanName), crashlyticsSpanProcessor)
  }
}
