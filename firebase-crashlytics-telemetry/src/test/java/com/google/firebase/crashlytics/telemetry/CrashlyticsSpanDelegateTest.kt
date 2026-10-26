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
import io.opentelemetry.api.common.AttributeKey.booleanKey
import io.opentelemetry.api.common.AttributeKey.doubleKey
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrashlyticsSpanDelegateTest {
  private val spanExporter = InMemorySpanExporter.create()
  private lateinit var tracer: Tracer

  private val linkedContext =
    SpanContext.create(
      "0af7651916cd43dd8448eb211c80319c",
      "b7ad6b7169203331",
      TraceFlags.getSampled(),
      TraceState.getDefault(),
    )

  @BeforeEach
  fun setUp() {
    val tracerProvider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build()
    // A zero pointer makes every persistence call a logged no-op, so no native library is needed.
    tracer =
      CrashlyticsTracerDelegate(
        tracerProvider.get("scope"),
        CrashlyticsSpanProcessor(MutationContext(0L)),
      )
  }

  @Test
  fun spanBuilder_fluentMethodsReturnSameBuilder() {
    val builder = tracer.spanBuilder("span")
    val calls: List<(SpanBuilder) -> SpanBuilder> =
      listOf(
        { it.setParent(Context.root()) },
        { it.setNoParent() },
        { it.addLink(linkedContext) },
        { it.addLink(linkedContext, Attributes.empty()) },
        { it.setAttribute("string", "value") },
        { it.setAttribute("long", 1L) },
        { it.setAttribute("double", 1.0) },
        { it.setAttribute("boolean", true) },
        { it.setAttribute(stringKey("typed"), "value") },
        { it.setAttribute(longKey("int"), 1) },
        { it.setAllAttributes(Attributes.of(stringKey("all"), "value")) },
        { it.setSpanKind(SpanKind.CLIENT) },
        { it.setStartTimestamp(1L, TimeUnit.SECONDS) },
        { it.setStartTimestamp(Instant.ofEpochSecond(1L)) },
      )

    for (call in calls) {
      assertThat(call(builder)).isSameInstanceAs(builder)
    }
  }

  @Test
  fun spanBuilder_chainedCalls_startCrashlyticsSpan() {
    val span =
      tracer
        .spanBuilder("span")
        .setNoParent()
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(stringKey("http.request.method"), "GET")
        .startSpan()

    assertThat(span).isInstanceOf(CrashlyticsSpanDelegate::class.java)
  }

  @Test
  fun span_fluentMethodsReturnSameSpan() {
    val span = tracer.spanBuilder("span").startSpan()
    val calls: List<(Span) -> Span> =
      listOf(
        { it.setAttribute("string", "value") },
        { it.setAttribute("long", 1L) },
        { it.setAttribute("double", 1.0) },
        { it.setAttribute("boolean", true) },
        { it.setAttribute(stringKey("typed"), "value") },
        { it.setAttribute(longKey("int"), 1) },
        { it.setAllAttributes(Attributes.of(stringKey("all"), "value")) },
        { it.addEvent("event") },
        { it.addEvent("event", Attributes.empty()) },
        { it.addEvent("event", 1L, TimeUnit.SECONDS) },
        { it.addEvent("event", Attributes.empty(), 1L, TimeUnit.SECONDS) },
        { it.setStatus(StatusCode.OK) },
        { it.setStatus(StatusCode.OK, "description") },
        { it.recordException(RuntimeException()) },
        { it.recordException(RuntimeException(), Attributes.empty()) },
        { it.updateName("renamed") },
        { it.addLink(linkedContext) },
        { it.addLink(linkedContext, Attributes.empty()) },
      )

    for (call in calls) {
      assertThat(call(span)).isSameInstanceAs(span)
    }
  }

  @Test
  fun span_addLink_recordsLink() {
    val span = tracer.spanBuilder("span").startSpan()

    span.addLink(linkedContext)
    span.addLink(linkedContext, Attributes.of(stringKey("link"), "value"))
    span.end()

    assertThat(exportedSpan().links.map { it.spanContext })
      .containsExactly(linkedContext, linkedContext)
  }

  @Test
  fun span_setAttribute_keepsAttributeTypes() {
    val span = tracer.spanBuilder("span").startSpan()

    span.setAttribute("long", 5L)
    span.setAttribute("double", 1.5)
    span.setAttribute("boolean", true)
    span.setAttribute(longKey("int"), 7)
    span.setAttribute(stringKey("typed"), "value")
    span.end()

    val attributes = exportedSpan().attributes
    assertThat(attributes.get(longKey("long"))).isEqualTo(5L)
    assertThat(attributes.get(doubleKey("double"))).isEqualTo(1.5)
    assertThat(attributes.get(booleanKey("boolean"))).isTrue()
    assertThat(attributes.get(longKey("int"))).isEqualTo(7L)
    assertThat(attributes.get(stringKey("typed"))).isEqualTo("value")
  }

  @Test
  fun span_chainedCalls_reachSdkSpan() {
    tracer
      .spanBuilder("span")
      .startSpan()
      .setStatus(StatusCode.ERROR)
      .setAttribute(stringKey("error.type"), "500")
      .end()

    val exported = exportedSpan()
    assertThat(exported.status.statusCode).isEqualTo(StatusCode.ERROR)
    assertThat(exported.attributes.get(stringKey("error.type"))).isEqualTo("500")
  }

  private fun exportedSpan(): SpanData = spanExporter.finishedSpanItems.single()
}
