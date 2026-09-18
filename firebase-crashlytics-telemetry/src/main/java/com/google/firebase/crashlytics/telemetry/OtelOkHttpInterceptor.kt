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

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD
import io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class OtelOkHttpInterceptor(private val openTelemetry: OpenTelemetry) : Interceptor {
  private val tracer = openTelemetry.getTracer("okhttp-interceptor")

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val span =
      tracer
        .spanBuilder("${request.method} ${request.url.encodedPath}")
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(HTTP_REQUEST_METHOD, request.method)
        .setAttribute(URL_FULL, request.url.toString())
        .startSpan()

    val context = Context.current().with(span)
    val requestBuilder = request.newBuilder()

    return try {
      context.makeCurrent().use {
        val response = chain.proceed(requestBuilder.build())
        span.setAttribute(HTTP_RESPONSE_STATUS_CODE, response.code.toLong())
        if (!response.isSuccessful) {
          span.setStatus(StatusCode.ERROR)
        }
        response
      }
    } catch (e: Exception) {
      span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
      span.recordException(e)
      throw e
    } finally {
      span.end()
    }
  }
}
