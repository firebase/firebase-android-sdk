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

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder

/**
 * A delegate wrapper around a SpanBuilder. This is an interim delegate to wrap a delegate around
 * each span.
 *
 * @note -
 *   https://kotlinlang.org/docs/delegation.html#overriding-a-member-of-an-interface-implemented-by-delegation
 *   indicates that if a method isn't implemented, Kotlin calls the delegate by default.
 */
class CrashlyticsSpanBuilderDelegate(
  private val delegate: SpanBuilder,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : SpanBuilder by delegate {
  override fun startSpan(): Span {
    return CrashlyticsSpanDelegate(delegate.startSpan(), crashlyticsSpanProcessor)
  }
}
