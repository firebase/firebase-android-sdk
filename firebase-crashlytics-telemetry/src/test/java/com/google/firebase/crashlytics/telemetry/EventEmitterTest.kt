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
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.LoggerBuilder
import io.opentelemetry.api.logs.LoggerProvider
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventEmitterTest {
  private lateinit var exporter: InMemoryLogRecordExporter
  private lateinit var emitter: EventEmitter

  @BeforeEach
  fun setUp() {
    exporter = InMemoryLogRecordExporter.create()
    emitter = EventEmitter(inMemoryOpenTelemetry(exporter))
  }

  @Test
  fun emit_recordsEventNameAttributesAndTimestamp() {
    emitter.emit(TelemetryEvent.CLICK, Attributes.of(stringKey("key"), "value"))

    val record = exporter.finishedLogRecordItems.single()
    assertThat(record.eventName).isEqualTo(TelemetryEvent.CLICK.semanticName)
    assertThat(record.attributes.get(stringKey("key"))).isEqualTo("value")
    assertThat(record.timestampEpochNanos).isGreaterThan(0L)
    assertThat(record.instrumentationScopeInfo.name)
      .isEqualTo(EventEmitter.INSTRUMENTATION_SCOPE_NAME)
  }

  @Test
  fun emit_withoutAttributes_recordsEmptyAttributes() {
    emitter.emit(TelemetryEvent.SCREEN_VIEW)

    assertThat(exporter.finishedLogRecordItems.single().attributes.isEmpty).isTrue()
  }

  @Test
  fun emit_whenLoggerThrows_doesNotThrow() {
    val throwingEmitter = EventEmitter(openTelemetryWithThrowingLogger())

    // Throwing here would fail the test.
    throwingEmitter.emit(TelemetryEvent.CLICK)
  }

  private fun openTelemetryWithThrowingLogger(): OpenTelemetry {
    val logger =
      object : Logger {
        override fun logRecordBuilder(): LogRecordBuilder = throw IllegalStateException("boom")
      }
    val loggerProvider = LoggerProvider { _ ->
      object : LoggerBuilder {
        override fun setSchemaUrl(schemaUrl: String): LoggerBuilder = this

        override fun setInstrumentationVersion(instrumentationScopeVersion: String): LoggerBuilder =
          this

        override fun build(): Logger = logger
      }
    }
    return object : OpenTelemetry {
      override fun getTracerProvider(): TracerProvider = TracerProvider.noop()

      override fun getPropagators(): ContextPropagators = ContextPropagators.noop()

      override fun getLogsBridge(): LoggerProvider = loggerProvider
    }
  }
}
