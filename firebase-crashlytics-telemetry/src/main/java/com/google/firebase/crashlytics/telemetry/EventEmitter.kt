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

import com.google.firebase.crashlytics.internal.Logger as CrashlyticsLogger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import java.util.concurrent.TimeUnit

/**
 * The events this SDK records.
 *
 * @property semanticName The OpenTelemetry event name.
 */
internal enum class TelemetryEvent(internal val semanticName: String) {
  /** The user landed on a new screen. */
  SCREEN_VIEW("app.navigation.complete"),

  /** The user tapped a UI element. */
  CLICK("app.widget.click"),
}

/**
 * Emits OpenTelemetry events (log records with an event name) for the public telemetry APIs.
 *
 * Failures are logged at debug level and swallowed, so recording telemetry never crashes the app.
 */
internal class EventEmitter(openTelemetry: OpenTelemetry) {
  private val logger: Logger = openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)

  fun emit(event: TelemetryEvent, attributes: Attributes = Attributes.empty()) {
    try {
      logger
        .logRecordBuilder()
        .setEventName(event.semanticName)
        .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .setAllAttributes(attributes)
        .emit()
    } catch (e: Exception) {
      CrashlyticsLogger.getLogger().d("Telemetry: failed to emit event: ${event.semanticName}", e)
    }
  }

  companion object {
    const val INSTRUMENTATION_SCOPE_NAME = "com.google.firebase.crashlytics.telemetry"
  }
}
