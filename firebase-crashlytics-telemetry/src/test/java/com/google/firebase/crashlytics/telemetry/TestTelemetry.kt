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
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter

/** Builds an OpenTelemetry SDK that synchronously exports log records to [exporter]. */
internal fun inMemoryOpenTelemetry(exporter: InMemoryLogRecordExporter): OpenTelemetrySdk =
  OpenTelemetrySdk.builder()
    .setLoggerProvider(
      SdkLoggerProvider.builder()
        .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
        .build()
    )
    .build()

/**
 * Creates a [FirebaseCrashlyticsTelemetry] for tests.
 *
 * Tests use this instead of the constructor, so constructor changes only need to be made here.
 */
internal fun createTestTelemetry(openTelemetry: OpenTelemetry): FirebaseCrashlyticsTelemetry =
  FirebaseCrashlyticsTelemetry(openTelemetry)
