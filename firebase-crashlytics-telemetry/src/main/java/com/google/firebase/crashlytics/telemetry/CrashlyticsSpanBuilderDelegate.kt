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

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * A delegate wrapper around a SpanBuilder. This is an interim delegate to wrap a delegate around
 * each span.
 *
 * @note
 * -
 * https://kotlinlang.org/docs/delegation.html#overriding-a-member-of-an-interface-implemented-by-delegation
 * indicates that if a method isn't implemented, Kotlin calls the delegate by default.
 * - Every fluent method returns this builder rather than the delegate's, so chained calls such as
 * `spanBuilder(name).setSpanKind(kind).startSpan()` still start a [CrashlyticsSpanDelegate]. The
 * Java default methods (`setAllAttributes`, `setStartTimestamp(Instant)`, and the `Int`
 * `setAttribute`) call back into the overrides below.
 */
internal class CrashlyticsSpanBuilderDelegate(
  private val delegate: SpanBuilder,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : SpanBuilder by delegate {
  override fun setParent(context: Context): SpanBuilder = apply { delegate.setParent(context) }

  override fun setNoParent(): SpanBuilder = apply { delegate.setNoParent() }

  override fun addLink(spanContext: SpanContext): SpanBuilder = apply {
    delegate.addLink(spanContext)
  }

  override fun addLink(spanContext: SpanContext, attributes: Attributes): SpanBuilder = apply {
    delegate.addLink(spanContext, attributes)
  }

  override fun setAttribute(key: String, value: String?): SpanBuilder = apply {
    delegate.setAttribute(key, value)
  }

  override fun setAttribute(key: String, value: Long): SpanBuilder = apply {
    delegate.setAttribute(key, value)
  }

  override fun setAttribute(key: String, value: Double): SpanBuilder = apply {
    delegate.setAttribute(key, value)
  }

  override fun setAttribute(key: String, value: Boolean): SpanBuilder = apply {
    delegate.setAttribute(key, value)
  }

  override fun <T> setAttribute(key: AttributeKey<T>, value: T?): SpanBuilder = apply {
    delegate.setAttribute(key, value)
  }

  override fun setSpanKind(spanKind: SpanKind): SpanBuilder = apply {
    delegate.setSpanKind(spanKind)
  }

  override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): SpanBuilder = apply {
    delegate.setStartTimestamp(startTimestamp, unit)
  }

  override fun startSpan(): Span {
    return CrashlyticsSpanDelegate(delegate.startSpan(), crashlyticsSpanProcessor)
  }
}
