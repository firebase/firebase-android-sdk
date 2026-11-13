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
import io.opentelemetry.api.common.AttributeKey.booleanKey
import io.opentelemetry.api.common.AttributeKey.doubleKey
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import java.util.concurrent.TimeUnit

/**
 * A delegate wrapper around a [Span] that notifies [crashlyticsSpanProcessor] of attribute updates.
 *
 * Kotlin `by delegate` only forwards abstract methods, so Java `default` methods on [Span] that do
 * not call back into an abstract method, such as `addLink`, are overridden explicitly.
 */
internal class CrashlyticsSpanDelegate(
  private val delegate: Span,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : Span by delegate {
  override fun setAttribute(key: String, value: String?): Span = apply {
    crashlyticsSpanProcessor.onAttributeAdded(delegate.spanContext.spanId, key, value.orEmpty())
    delegate.setAttribute(key, value)
  }

  override fun setAttribute(key: String, value: Long): Span = setAttribute(longKey(key), value)

  override fun setAttribute(key: String, value: Double): Span = setAttribute(doubleKey(key), value)

  override fun setAttribute(key: String, value: Boolean): Span =
    setAttribute(booleanKey(key), value)

  override fun setAttribute(key: AttributeKey<Long?>, value: Int): Span =
    setAttribute(key, value.toLong())

  override fun <T> setAttribute(key: AttributeKey<T>, value: T?): Span = apply {
    if (value != null) {
      crashlyticsSpanProcessor.onAttributeAdded(
        delegate.spanContext.spanId,
        key.key,
        value.toString(),
      )
    }
    delegate.setAttribute(key, value)
  }

  override fun setAllAttributes(attributes: Attributes): Span = apply {
    crashlyticsSpanProcessor.onAttributesAdded(delegate.spanContext.spanId, attributes)
    delegate.setAllAttributes(attributes)
  }

  override fun addEvent(name: String, attributes: Attributes): Span = apply {
    delegate.addEvent(name, attributes)
  }

  override fun addEvent(
    name: String,
    attributes: Attributes,
    timestamp: Long,
    unit: TimeUnit,
  ): Span = apply { delegate.addEvent(name, attributes, timestamp, unit) }

  override fun setStatus(statusCode: StatusCode, description: String): Span = apply {
    delegate.setStatus(statusCode, description)
  }

  override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span =
    apply {
      delegate.recordException(exception, additionalAttributes)
    }

  override fun updateName(name: String): Span = apply { delegate.updateName(name) }

  override fun addLink(spanContext: SpanContext): Span = apply { delegate.addLink(spanContext) }

  override fun addLink(spanContext: SpanContext, attributes: Attributes): Span = apply {
    delegate.addLink(spanContext, attributes)
  }
}
