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
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A LogRecordProcessor that logs records to Logcat for debugging.
 *
 * TODO: The Crashlytics RUM effort doesn't currently support logs.
 */
internal class CrashlyticsLogRecordProcessor : LogRecordProcessor {
  override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
    val logRecordData = logRecord.toLogRecordData()
    val body = logRecordData.bodyValue
    val attributes = logRecordData.attributes.toString()
    val eventName = logRecordData.eventName
    Log.d(
      "CrashlyticsLogRecordProcessor",
      "LOG: name=$eventName, body=$body, attributes=$attributes",
    )
  }

  override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

  override fun forceFlush(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
