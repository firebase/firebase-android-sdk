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
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrashlyticsOpenTelemetryDelegateTest {
  private val logExporter = InMemoryLogRecordExporter.create()
  private val spanExporter = InMemorySpanExporter.create()
  private lateinit var sdk: OpenTelemetrySdk
  private lateinit var delegate: CrashlyticsOpenTelemetryDelegate

  @BeforeEach
  fun setUp() {
    sdk =
      OpenTelemetrySdk.builder()
        .setTracerProvider(
          SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        )
        .setLoggerProvider(
          SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
            .build()
        )
        .build()
    // A zero pointer makes every persistence call a logged no-op, so no native library is needed.
    delegate = CrashlyticsOpenTelemetryDelegate(sdk, CrashlyticsSpanProcessor(MutationContext(0L)))
  }

  @Test
  fun recordClick_emitsLogRecordThroughDelegate() {
    createTestTelemetry(delegate).recordClick("checkout_button")

    assertThat(logExporter.finishedLogRecordItems).hasSize(1)
    assertThat(logExporter.finishedLogRecordItems.single().eventName).isEqualTo("app.widget.click")
  }

  @Test
  fun recordScreenAppear_emitsLogRecordThroughDelegate() {
    createTestTelemetry(delegate).recordScreenAppear("CheckoutScreen")

    assertThat(logExporter.finishedLogRecordItems).hasSize(1)
  }

  @Test
  fun getTracer_withName_startsCrashlyticsSpan() {
    val span = delegate.getTracer("scope").spanBuilder("span").startSpan()

    assertThat(span).isInstanceOf(CrashlyticsSpanDelegate::class.java)
  }

  @Test
  fun getTracer_withNameAndVersion_startsCrashlyticsSpan() {
    val span = delegate.getTracer("scope", "1.0").spanBuilder("span").startSpan()

    assertThat(span).isInstanceOf(CrashlyticsSpanDelegate::class.java)
  }

  @Test
  fun tracerBuilder_startsCrashlyticsSpan() {
    val tracer =
      delegate.tracerBuilder("scope").setInstrumentationVersion("1.0").setSchemaUrl("url").build()

    assertThat(tracer.spanBuilder("span").startSpan())
      .isInstanceOf(CrashlyticsSpanDelegate::class.java)
  }

  @Test
  fun tracerProvider_get_startsCrashlyticsSpan() {
    val span = delegate.tracerProvider.get("scope").spanBuilder("span").startSpan()

    assertThat(span).isInstanceOf(CrashlyticsSpanDelegate::class.java)
  }

  @Test
  fun getTracer_spansAreExportedBySdk() {
    delegate.getTracer("scope").spanBuilder("span").startSpan().end()

    assertThat(spanExporter.finishedSpanItems.map { it.name }).containsExactly("span")
  }

  @Test
  fun meterProvider_isForwardedToSdk() {
    assertThat(delegate.meterProvider).isSameInstanceAs(sdk.meterProvider)
  }
}
