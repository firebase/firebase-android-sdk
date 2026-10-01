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
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.Span as OtelSpan
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NetworkRequestTest {
  private val exporter = InMemorySpanExporter.create()
  private val openTelemetry =
    OpenTelemetrySdk.builder()
      .setTracerProvider(
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
      )
      .build()
  private val telemetry = createTestTelemetry(openTelemetry)

  private fun onlySpan(): SpanData = exporter.finishedSpanItems.single()

  @Test
  fun success_recordsClientRootSpanAndReturnsResult() =
    runBlocking<Unit> {
      val result =
        telemetry.networkRequest("https://api.example.com/users", method = "POST") {
          responseCode = 201
          "created"
        }

      assertThat(result).isEqualTo("created")
      val span = onlySpan()
      assertThat(span.name).isEqualTo("POST")
      assertThat(span.kind).isEqualTo(SpanKind.CLIENT)
      assertThat(span.parentSpanContext.isValid).isFalse()
      assertThat(span.attributes.get(stringKey("http.request.method"))).isEqualTo("POST")
      assertThat(span.attributes.get(stringKey("url.full")))
        .isEqualTo("https://api.example.com/users")
      assertThat(span.attributes.get(longKey("http.response.status_code"))).isEqualTo(201L)
      assertThat(span.attributes.get(stringKey("error.type"))).isNull()
      assertThat(span.status.statusCode).isEqualTo(StatusCode.UNSET)
    }

  @Test
  fun defaults_toGetAndMethodAsSpanName() =
    runBlocking<Unit> {
      telemetry.networkRequest("https://api.example.com") {}

      assertThat(onlySpan().name).isEqualTo("GET")
      assertThat(onlySpan().attributes.get(stringKey("http.request.method"))).isEqualTo("GET")
    }

  @Test
  fun name_isUsedAsSpanName() =
    runBlocking<Unit> {
      telemetry.networkRequest("https://api.example.com/me", name = "profile fetch") {}

      assertThat(onlySpan().name).isEqualTo("profile fetch")
    }

  @Test
  fun errorResponseCode_marksFailure() =
    runBlocking<Unit> {
      telemetry.networkRequest("https://api.example.com/missing") { responseCode = 404 }

      val span = onlySpan()
      assertThat(span.attributes.get(longKey("http.response.status_code"))).isEqualTo(404L)
      assertThat(span.attributes.get(stringKey("error.type"))).isEqualTo("404")
      assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
    }

  @Test
  fun thrownException_isRecordedAndRethrownUnchanged() {
    val failure = IOException("connection reset")

    val thrown =
      assertThrows(IOException::class.java) {
        runBlocking { telemetry.networkRequest<Unit>("https://api.example.com") { throw failure } }
      }

    assertThat(thrown).hasMessageThat().isEqualTo("connection reset")
    val span = onlySpan()
    assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
    assertThat(span.attributes.get(stringKey("error.type"))).isEqualTo("java.io.IOException")
    assertThat(span.attributes.get(longKey("http.response.status_code"))).isNull()
    assertThat(span.events.map { it.name }).containsExactly("exception")
  }

  @Test
  fun setNetworkException_recordsFailureWithoutThrowing() =
    runBlocking<Unit> {
      val result =
        telemetry.networkRequest("https://api.example.com/items", method = "POST") {
          responseCode = 200
          setNetworkException(IllegalStateException("parse error"))
          "partial data"
        }

      assertThat(result).isEqualTo("partial data")
      val span = onlySpan()
      assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
      assertThat(span.attributes.get(stringKey("error.type")))
        .isEqualTo("java.lang.IllegalStateException")
      assertThat(span.attributes.get(longKey("http.response.status_code"))).isEqualTo(200L)
      assertThat(span.events.map { it.name }).containsExactly("exception")
    }

  @Test
  fun cancellation_isRethrownWithoutMarkingFailure() {
    assertThrows(CancellationException::class.java) {
      runBlocking {
        telemetry.networkRequest<Unit>("https://api.example.com") {
          throw CancellationException("cancelled")
        }
      }
    }

    val span = onlySpan()
    assertThat(span.status.statusCode).isEqualTo(StatusCode.UNSET)
    assertThat(span.events).isEmpty()
  }

  @Test
  fun span_isCurrentInsideBlock() =
    runBlocking<Unit> {
      val insideContext =
        telemetry.networkRequest("https://api.example.com") { OtelSpan.current().spanContext }

      assertThat(insideContext).isEqualTo(onlySpan().spanContext)
    }

  @Test
  fun span_isRootEvenWithActiveParent() =
    runBlocking<Unit> {
      val parent = openTelemetry.getTracer("test").spanBuilder("parent").startSpan()
      parent.makeCurrent().use { telemetry.networkRequest("https://api.example.com") {} }
      parent.end()

      val request = exporter.finishedSpanItems.single { it.name == "GET" }
      assertThat(request.parentSpanContext.isValid).isFalse()
    }
}
