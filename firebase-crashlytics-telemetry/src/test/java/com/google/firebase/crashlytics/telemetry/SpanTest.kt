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

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.Span as OtelSpan
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SpanTest {

  companion object {
    // TODO: OpenTelemetry has a JUnit 4 rule, and so this may not be necessary.
    @RegisterExtension val otelExtension: OpenTelemetryExtension = OpenTelemetryExtension.create()
    const val INVALID_PARENT_SPAN_ID = "0000000000000000"
  }

  @BeforeEach
  fun setUp() {
    otelExtension.clearSpans()
    OtelSpan.getInvalid().makeCurrent()
  }

  private val tracer = otelExtension.openTelemetry.getTracer("test-tracer")

  @Test
  fun spanDataConstructor_withValidSpanData_mapsAllProperties() {
    val spans = generateSpans(5)

    assertThat(spans.size).isEqualTo(5)

    val crashlyticsSpans = spans.map { Span(it) }

    spans.zip(crashlyticsSpans).forEach { (originalSpanData, crashlyticsSpan) ->
      // Verify custom properties on Span
      assertThat(originalSpanData.spanId).isEqualTo(crashlyticsSpan.spanId)
      assertThat(originalSpanData.parentSpanContext.spanId).isEqualTo(crashlyticsSpan.parentSpanId)
      assertThat(originalSpanData.name).isEqualTo(crashlyticsSpan.name)
      assertThat(originalSpanData.traceId).isEqualTo(crashlyticsSpan.traceId)
      assertThat(originalSpanData.attributes.size()).isEqualTo(crashlyticsSpan.attributes.size)
      assertThat(originalSpanData.startEpochNanos).isEqualTo(crashlyticsSpan.startTime)

      // Verify the generated SpanData matches the original SpanData
      val generatedSpanData = crashlyticsSpan.toSpanData()
      assertSpanDataEquals(originalSpanData, generatedSpanData)
    }
  }

  @Test
  fun toHexId_withLongValue_preservesUnsignedValueRoundtrip() {
    val testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.setAttribute("test_key", "test_value")
    testSpan.end()

    val spans = otelExtension.spans
    assertThat(spans).isNotEmpty()
    val span = spans.first()

    // This spanId represents the span when stored in an mmap.
    val longSpanId = span.spanId.toULong(16).toLong()
    val longParentSpanId = span.parentSpanId.toULong(16).toLong()

    // This spanId represents the span after it's recovered from an mmap.
    val recoveredSpanId = longSpanId.toHexId()
    val recoveredParentSpanId = longParentSpanId.toHexId()

    assertThat(span.spanId).isEqualTo(recoveredSpanId)
    assertThat(span.parentSpanId).isEqualTo(recoveredParentSpanId)
  }

  @Test
  fun generateSpans_withHierarchicalTraces_maintainsTraceAndParentIds() {
    val spans = generateSpans()
    spans.forEach { span -> assertThat(span.traceId).isEqualTo(spans.first().traceId) }
    assertThat(spans.count { it.parentSpanId.equals(INVALID_PARENT_SPAN_ID) }).isEqualTo(1)

    var parentSpanId = INVALID_PARENT_SPAN_ID
    spans.reversed().forEach {
      assertThat(it.parentSpanContext.spanId).isEqualTo(parentSpanId)
      parentSpanId = it.spanId
    }
  }

  @Test
  fun spanDataConstructor_withHierarchicalSpans_maintainsTraceAndParentIds() {
    val spans = generateSpans()
    val crashlyticsSpans = spans.map { Span(it) }

    crashlyticsSpans.forEach { assertThat(it.traceId).isEqualTo(crashlyticsSpans.first().traceId) }

    var parentSpanIdStr = INVALID_PARENT_SPAN_ID
    crashlyticsSpans.reversed().forEach {
      assertThat(it.parentSpanId).isEqualTo(parentSpanIdStr)
      parentSpanIdStr = it.spanId
    }

    var parentSpanId = INVALID_PARENT_SPAN_ID
    crashlyticsSpans.reversed().forEach {
      val spanData = it.toSpanData()
      assertThat(spanData.parentSpanContext.spanId).isEqualTo(parentSpanId)
      parentSpanId = spanData.spanContext.spanId
    }
  }

  @Test
  fun createRecoveredSpan_withLongIds_mapsAllPropertiesCorrectly() {
    val traceIdHigh = 0x1234567890abcdefL
    val traceIdLow = 0xfedcba0987654321UL.toLong()
    val spanId = 0x1122334455667788L
    val parentSpanId = 0x99aabbccddeeff00UL.toLong()
    val startTime = 123456789L
    val name = "recovered_span"
    val attributes = arrayOf("key1", "val1", "key2", "val2")

    val span =
      Span.createRecoveredSpan(
        traceIdHigh = traceIdHigh,
        traceIdLow = traceIdLow,
        spanId = spanId,
        parentSpanId = parentSpanId,
        startTime = startTime,
        name = name,
        attributes = attributes,
      )

    assertThat(span.traceId).isEqualTo("1234567890abcdeffedcba0987654321")
    assertThat(span.spanId).isEqualTo("1122334455667788")
    assertThat(span.parentSpanId).isEqualTo("99aabbccddeeff00")
    assertThat(span.startTime).isEqualTo(startTime)
    assertThat(span.name).isEqualTo(name)
    assertThat(span.attributes).containsExactly("key1", "val1", "key2", "val2")
  }

  private fun assertSpanDataEquals(expected: SpanData, actual: SpanData) {
    assertThat(actual.traceId).isEqualTo(expected.traceId)
    assertThat(actual.spanId).isEqualTo(expected.spanId)
    assertThat(actual.parentSpanId).isEqualTo(expected.parentSpanId)
    assertThat(actual.name).isEqualTo(expected.name)
    assertThat(actual.startEpochNanos).isEqualTo(expected.startEpochNanos)
    // TODO: Fix end time.
    //    assertThat(actual.endEpochNanos).isEqualTo(expected.endEpochNanos)
    assertThat(actual.attributes.size()).isEqualTo(expected.attributes.size())
    expected.attributes.asMap().forEach { (key, value) ->
      assertThat(actual.attributes.get(key)).isEqualTo(value)
    }
    assertThat(actual.parentSpanContext.spanId).isEqualTo(expected.parentSpanContext.spanId)
    assertThat(actual.spanContext.spanId).isEqualTo(expected.spanContext.spanId)
  }

  private fun generateSpans(count: Int = 20): List<SpanData> {
    val activeSpans = mutableListOf<OtelSpan>()

    // Start spans in a hierarchical order.
    for (i in 1..count) {
      // TODO: Explore setting custom timestamps.
      val span = tracer.spanBuilder("test_span_$i").startSpan()
      for (j in 1..5) {
        span.setAttribute("string key $j", "value $j")
      }

      activeSpans.add(span)
      activeSpans.lastOrNull()?.makeCurrent()
    }

    // End spans in the
    while (activeSpans.isNotEmpty()) {
      val span = activeSpans.removeLast()
      span.end()
      activeSpans.lastOrNull()?.makeCurrent()
    }

    // TODO: Verify why test_span_1 is returned as current span even after ending it.
    //    assertThat(Span.current()).isEqualTo(null)
    return otelExtension.spans
  }
}
