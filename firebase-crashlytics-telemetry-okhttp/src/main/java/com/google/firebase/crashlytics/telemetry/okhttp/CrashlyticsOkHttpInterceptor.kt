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

import com.google.firebase.crashlytics.internal.Logger
import com.google.firebase.crashlytics.telemetry.FirebaseCrashlyticsTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE
import io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD
import io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Records each OkHttp request as a span. Requests made inside a
 * [FirebaseCrashlyticsTelemetry.networkRequest] block become children of its span.
 *
 * @property tracePropagationTargets Host regexes that receive W3C `traceparent` headers. Empty by
 * default.
 */
public class CrashlyticsOkHttpInterceptor
internal constructor(
  public val tracePropagationTargets: List<String>,
  private val tracerProvider: () -> Tracer,
) : Interceptor {
  private val propagationHosts = tracePropagationTargets.map(::Regex)
  @Volatile private var tracer: Tracer? = null

  /** Creates an interceptor that does not send W3C `traceparent` headers. */
  public constructor() : this(emptyList())

  /**
   * Creates an interceptor that sends W3C `traceparent` headers to hosts matching
   * [tracePropagationTargets].
   */
  public constructor(
    tracePropagationTargets: List<String>
  ) : this(
    tracePropagationTargets,
    { FirebaseCrashlyticsTelemetry.instance.getTracer(INSTRUMENTATION_SCOPE_NAME) },
  )

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val tracer = tracer() ?: return chain.proceed(request)

    val span =
      tracer
        .spanBuilder(request.method)
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(HTTP_REQUEST_METHOD, request.method)
        .setAttribute(URL_FULL, request.url.toString())
        .startSpan()
    val context = Context.current().with(span)
    val tracedRequest =
      if (propagationHosts.any { it.matches(request.url.host) }) {
        request.newBuilder().also { PROPAGATOR.inject(context, it, HEADER_SETTER) }.build()
      } else {
        request
      }

    try {
      val response = context.makeCurrent().use { chain.proceed(tracedRequest) }
      val code = response.code
      span.setAttribute(HTTP_RESPONSE_STATUS_CODE, code.toLong())
      if (code >= 400) {
        span.setAttribute(ERROR_TYPE, code.toString())
        span.setStatus(StatusCode.ERROR)
      }
      return response
    } catch (e: Throwable) {
      span.recordException(e)
      span.setAttribute(ERROR_TYPE, e.javaClass.name)
      span.setStatus(StatusCode.ERROR)
      throw e
    } finally {
      span.end()
    }
  }

  private fun tracer(): Tracer? =
    tracer
      ?: try {
        tracerProvider().also { tracer = it }
      } catch (e: RuntimeException) {
        Logger.getLogger().d("Telemetry: OkHttp request not traced", e)
        null
      }

  private companion object {
    const val INSTRUMENTATION_SCOPE_NAME = "com.google.firebase.crashlytics.telemetry.okhttp"
    val PROPAGATOR: W3CTraceContextPropagator = W3CTraceContextPropagator.getInstance()
    val HEADER_SETTER =
      TextMapSetter<Request.Builder> { builder, key, value -> builder?.header(key, value) }
  }
}

/** Adds a [CrashlyticsOkHttpInterceptor] that does not send W3C `traceparent` headers. */
public fun OkHttpClient.Builder.addFirebaseCrashlyticsTelemetry(): OkHttpClient.Builder =
  addInterceptor(CrashlyticsOkHttpInterceptor())

/**
 * Adds a [CrashlyticsOkHttpInterceptor] that sends W3C `traceparent` headers to hosts matching
 * [tracePropagationTargets].
 */
public fun OkHttpClient.Builder.addFirebaseCrashlyticsTelemetry(
  tracePropagationTargets: List<String>
): OkHttpClient.Builder = addInterceptor(CrashlyticsOkHttpInterceptor(tracePropagationTargets))
