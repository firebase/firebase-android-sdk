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

/**
 * A delegate that allows listening to changes in an in flight span. Currently this only listens to
 * attribute changes.
 *
 * @note -
 *   https://kotlinlang.org/docs/delegation.html#overriding-a-member-of-an-interface-implemented-by-delegation
 *   indicates that if a method isn't implemented, Kotlin calls the delegate by default.
 */
class CrashlyticsSpanDelegate(
  private val delegate: Span,
  private val crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
) : Span by delegate {
  override fun setAttribute(key: String, value: String?): Span {
    crashlyticsSpanProcessor.onAttributeAdded(delegate.spanContext.spanId, key, value.orEmpty())
    return delegate.setAttribute(key, value)
  }

  override fun setAllAttributes(attributes: Attributes): Span? {
    crashlyticsSpanProcessor.onAttributesAdded(delegate.spanContext.spanId, attributes)
    return delegate.setAllAttributes(attributes)
  }

  override fun setAttribute(key: String, value: Long): Span {
    return this.setAttribute(key, value.toString())
  }

  override fun setAttribute(key: String, value: Double): Span {
    return this.setAttribute(key, value.toString())
  }

  override fun setAttribute(key: String, value: Boolean): Span {
    return this.setAttribute(key, value.toString())
  }

  override fun setAttribute(key: AttributeKey<Long?>, value: Int): Span {
    return this.setAttribute(key.toString(), value.toString())
  }
}
