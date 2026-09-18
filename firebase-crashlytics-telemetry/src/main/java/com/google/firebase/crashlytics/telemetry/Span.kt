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

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import java.util.Locale

/**
 * Represents a Span (both mutable session spans and recovered spans are identical). Enforces strict
 * traceId hexadecimal and length validation upon initialization to catch failures at the
 * configuration boundary.
 */
data class Span(
  val traceId: String,
  val spanId: String,
  val parentSpanId: String,
  val startTime: Long,
  val name: String,
  val attributes: Map<String, String>,
) {

  constructor(
    spanData: SpanData
  ) : this(
    traceId = spanData.traceId,
    spanId = spanData.spanId,
    parentSpanId =
      if (spanData.parentSpanContext.isValid) {
        spanData.parentSpanContext.spanId
      } else {
        0L.toHexId()
      },
    startTime = spanData.startEpochNanos,
    name = spanData.name,
    attributes =
      spanData.attributes.asMap().mapKeys { it.key.key }.mapValues { it.value.toString() },
  )

  init {
    require(
      traceId.length == 32 && traceId.all { it.isDigit() || it.lowercase(Locale.US) in "abcdef" }
    ) {
      "Invalid Trace ID: $traceId. Must be a 32-character hexadecimal string."
    }
    require(
      spanId.length == 16 && spanId.all { it.isDigit() || it.lowercase(Locale.US) in "abcdef" }
    ) {
      "Invalid Span ID: $spanId. Must be a 16-character hexadecimal string."
    }
    require(
      parentSpanId.length == 16 &&
        parentSpanId.all { it.isDigit() || it.lowercase(Locale.US) in "abcdef" }
    ) {
      "Invalid Parent Span ID: $parentSpanId. Must be a 16-character hexadecimal string."
    }
  }

  fun toSpanData(): SpanData = SpanDataDelegate(this)

  companion object {
    @JvmStatic
    fun createRecoveredSpan(
      traceIdHigh: Long,
      traceIdLow: Long,
      spanId: Long,
      parentSpanId: Long,
      startTime: Long,
      name: String,
      attributes: Array<String>,
    ): Span {
      return Span(
        traceId = traceIdHigh.toHexId() + traceIdLow.toHexId(),
        spanId = spanId.toHexId(),
        parentSpanId = parentSpanId.toHexId(),
        startTime = startTime,
        name = name,
        attributes =
          buildMap {
            for (i in 1..attributes.lastIndex step 2) {
              put(attributes[i - 1], attributes[i])
            }
          },
      )
    }
  }
}

// region Helpers

fun Long.toHexId(): String = this.toULong().toString(radix = 16).padStart(16, '0')

private class SpanDataDelegate(private val span: Span) : SpanData {
  override fun getTraceId(): String = span.traceId

  override fun getSpanId(): String = span.spanId

  override fun getParentSpanId(): String = span.parentSpanId

  override fun getName(): String = span.name

  override fun getStartEpochNanos(): Long = span.startTime

  // TODO: Fix end timestamp
  override fun getEndEpochNanos(): Long = span.startTime + 1000L

  override fun getAttributes(): Attributes {
    val builder = Attributes.builder()
    span.attributes.forEach { (key, value) -> builder.put(key, value) }
    return builder.build()
  }

  override fun getTotalAttributeCount(): Int = span.attributes.size

  // TODO: Should this always show as ended?
  override fun hasEnded(): Boolean = true

  // TODO: This should only be client if it's a network request,
  // otherwise it should be internal.
  override fun getKind(): SpanKind = SpanKind.CLIENT

  // TODO: Check if this should be ok, unset or error.
  override fun getStatus(): StatusData = StatusData.unset()

  override fun getParentSpanContext(): SpanContext {
    return SpanContext.create(
      span.traceId,
      span.parentSpanId,
      // TODO: Update this if we decide to persist these values. Otherwise they'll be lost.
      TraceFlags.getDefault(),
      TraceState.getDefault(),
    )
  }

  override fun getSpanContext(): SpanContext {
    return SpanContext.create(
      span.traceId,
      span.spanId,
      TraceFlags.getDefault(),
      TraceState.getDefault(),
    )
  }

  // Methods below all return a default / empty value.
  override fun getResource(): Resource = Resource.getDefault()

  override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo =
    InstrumentationScopeInfo.empty()

  override fun getEvents(): List<EventData> = emptyList()

  override fun getLinks(): List<LinkData> = emptyList()

  override fun getTotalRecordedEvents(): Int = 0

  override fun getTotalRecordedLinks(): Int = 0

  @Deprecated("Deprecated in Java")
  override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo? = null
}

// region endregion
