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

package com.google.firebase.crashlytics.telemetry.okhttp

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CrashlyticsOkHttpInterceptorTest {
  private val exporter = InMemorySpanExporter.create()
  private val tracer: Tracer =
    SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(exporter))
      .build()
      .get("test")

  private var sentRequest: Request? = null

  private fun respondWith(code: Int) = Interceptor { chain ->
    sentRequest = chain.request()
    Response.Builder()
      .request(chain.request())
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message("")
      .body("".toResponseBody())
      .build()
  }

  private fun client(
    network: Interceptor = respondWith(200),
    targets: List<String> = emptyList(),
    tracerProvider: () -> Tracer = { tracer },
  ): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor(CrashlyticsOkHttpInterceptor(targets, tracerProvider))
      .addInterceptor(network)
      .build()

  private fun OkHttpClient.get(url: String) =
    newCall(Request.Builder().url(url).build()).execute().close()

  private fun onlySpan(): SpanData = exporter.finishedSpanItems.single()

  @Test
  fun success_recordsClientRootSpan() {
    client().get("https://api.example.com/users/42")

    val span = onlySpan()
    assertThat(span.name).isEqualTo("GET")
    assertThat(span.kind).isEqualTo(SpanKind.CLIENT)
    assertThat(span.parentSpanContext.isValid).isFalse()
    assertThat(span.attributes.get(stringKey("http.request.method"))).isEqualTo("GET")
    assertThat(span.attributes.get(stringKey("url.full")))
      .isEqualTo("https://api.example.com/users/42")
    assertThat(span.attributes.get(longKey("http.response.status_code"))).isEqualTo(200L)
    assertThat(span.status.statusCode).isEqualTo(StatusCode.UNSET)
  }

  @Test
  fun errorResponseCode_marksFailure() {
    client(network = respondWith(503)).get("https://api.example.com")

    val span = onlySpan()
    assertThat(span.attributes.get(longKey("http.response.status_code"))).isEqualTo(503L)
    assertThat(span.attributes.get(stringKey("error.type"))).isEqualTo("503")
    assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
  }

  @Test
  fun transportError_isRecordedAndRethrown() {
    val failing = Interceptor { throw IOException("connection reset") }

    val thrown =
      assertThrows(IOException::class.java) {
        client(network = failing).get("https://api.example.com")
      }

    assertThat(thrown).hasMessageThat().isEqualTo("connection reset")
    val span = onlySpan()
    assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
    assertThat(span.attributes.get(stringKey("error.type"))).isEqualTo("java.io.IOException")
    assertThat(span.events.map { it.name }).containsExactly("exception")
  }

  @Test
  fun matchingHost_receivesTraceparentForRequestSpan() {
    client(targets = listOf("api\\.example\\.com")).get("https://api.example.com")

    val span = onlySpan()
    val flags = span.spanContext.traceFlags.asHex()
    assertThat(sentRequest!!.header("traceparent"))
      .isEqualTo("00-${span.traceId}-${span.spanId}-$flags")
  }

  @Test
  fun noTargets_tracesWithoutTraceparent() {
    client().get("https://api.example.com")

    assertThat(sentRequest!!.header("traceparent")).isNull()
    assertThat(exporter.finishedSpanItems).hasSize(1)
  }

  @Test
  fun otherHost_doesNotReceiveTraceparent() {
    client(targets = listOf("api\\.example\\.com")).get("https://cdn.example.net")

    assertThat(sentRequest!!.header("traceparent")).isNull()
    assertThat(exporter.finishedSpanItems).hasSize(1)
  }

  @Test
  fun activeSpan_isParent() {
    val parent = tracer.spanBuilder("parent").startSpan()
    parent.makeCurrent().use { client().get("https://api.example.com") }
    parent.end()

    val request = exporter.finishedSpanItems.single { it.name == "GET" }
    assertThat(request.parentSpanContext).isEqualTo(parent.spanContext)
  }

  @Test
  fun telemetryUnavailable_requestProceedsUntraced() {
    val unavailable: () -> Tracer = { throw IllegalStateException("No FirebaseApp") }

    client(tracerProvider = unavailable).get("https://api.example.com")

    assertThat(sentRequest).isNotNull()
    assertThat(sentRequest!!.header("traceparent")).isNull()
    assertThat(exporter.finishedSpanItems).isEmpty()
  }

  @Test
  fun telemetryUnavailableThenAvailable_laterRequestsAreTraced() {
    var available = false
    val client =
      client(
        tracerProvider = {
          check(available) { "No FirebaseApp" }
          tracer
        }
      )

    client.get("https://api.example.com/first")
    available = true
    client.get("https://api.example.com/second")

    assertThat(exporter.finishedSpanItems.map { it.attributes.get(stringKey("url.full")) })
      .containsExactly("https://api.example.com/second")
  }

  @Test
  fun tracer_isObtainedOnce() {
    var calls = 0
    val client =
      client(
        tracerProvider = {
          calls++
          tracer
        }
      )

    client.get("https://api.example.com/first")
    client.get("https://api.example.com/second")

    assertThat(calls).isEqualTo(1)
    assertThat(exporter.finishedSpanItems).hasSize(2)
  }
}
