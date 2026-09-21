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

import android.util.Log
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [SpanProcessor] that coordinates with Crashlytics, writing active span states to the
 * file-backed mutable memory mapping via the injected [mutationContext].
 */
internal class CrashlyticsSpanProcessor(private val mutationContext: MutationContext) :
  SpanProcessor {
  val scope = CoroutineScope(Dispatchers.Default)

  override fun onStart(parentContext: Context, span: ReadWriteSpan) {
    span.setAttribute("gcp.firebase.app_version", "1.0")

    val details =
      "name=${span.name}, " +
        "traceId=${span.spanContext.traceId}, " +
        "spanId=${span.spanContext.spanId}, " +
        "attributes=${span.toSpanData().attributes}, " +
        "status=${span.toSpanData().status}, " +
        "events=${span.toSpanData().events}"
    Log.d("CrashlyticsSpanProcessor", "START SPAN: $details")

    val persistSpan = Span(span.toSpanData())
    scope.launch { mutationContext.addSpan(persistSpan) }
  }

  fun onAttributeAdded(spanId: String, key: String, value: String) {
    scope.launch { mutationContext.setAttributeOnSpan(spanId, key, value) }
  }

  fun onAttributesAdded(spanId: String, attributes: Attributes) {
    scope.launch {
      for (attribute in attributes.asMap()) {
        mutationContext.setAttributeOnSpan(
          spanId,
          attribute.key.toString(),
          attribute.value.toString(),
        )
      }
    }
  }

  override fun isStartRequired(): Boolean = true

  override fun onEnd(span: ReadableSpan) {
    val details =
      "name=${span.name}, " +
        "traceId=${span.spanContext.traceId}, " +
        "spanId=${span.spanContext.spanId}, " +
        "attributes=${span.toSpanData().attributes}, " +
        "status=${span.toSpanData().status}, " +
        "events=${span.toSpanData().events}"
    Log.d("CrashlyticsSpanProcessor", "END SPAN: $details")

    // TODO: Add logic here (or in the Exporter) to decide whether to upload a completed span
    //  (or not).
    scope.launch { mutationContext.endSpan(span.spanContext.spanId) }
  }

  override fun isEndRequired(): Boolean = true

  override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

  override fun forceFlush(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
