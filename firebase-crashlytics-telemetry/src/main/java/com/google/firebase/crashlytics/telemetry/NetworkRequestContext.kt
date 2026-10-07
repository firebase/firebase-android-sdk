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

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE
import io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE

/**
 * Receiver of a [FirebaseCrashlyticsTelemetry.traceRequest] block.
 *
 * @property url The request URL.
 * @property method The HTTP method.
 */
public class NetworkRequestContext
internal constructor(
  public val url: String,
  public val method: String,
  private val span: Span,
) {
  /** The HTTP response status code. Codes of 400 or above mark the request as failed. */
  public var responseCode: Int? = null

  /** Records [throwable] and marks the request as failed without throwing. */
  public fun setNetworkException(throwable: Throwable) {
    recordError(throwable)
  }

  internal fun recordError(throwable: Throwable) {
    span.recordException(throwable)
    span.setAttribute(ERROR_TYPE, throwable.javaClass.name)
    span.setStatus(StatusCode.ERROR)
  }

  internal fun recordResponseCode() {
    val code = responseCode ?: return
    span.setAttribute(HTTP_RESPONSE_STATUS_CODE, code.toLong())
    if (code >= 400) {
      span.setAttribute(ERROR_TYPE, code.toString())
      span.setStatus(StatusCode.ERROR)
    }
  }
}
