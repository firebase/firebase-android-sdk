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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val BASE_ENDPOINT = "https://firebasetelemetry.googleapis.com"

internal object OpenTelemetryManager {
  private val scope = CoroutineScope(Dispatchers.Default)

  // This is run, and not lazy, to initialize persistence and upload previous crashes ASAP.
  val instance: OpenTelemetry = run {
    val firebaseApp = getFirebaseApp()

    val options = firebaseApp.options
    val projectId =
      requireNotNull(options.projectId) {
        "FirebaseOptions.projectId is required to initialize Crashlytics Telemetry"
      }
    val googleAppId = options.applicationId
    val apiKey = options.apiKey
    val cacheDir = firebaseApp.applicationContext.cacheDir

    val resource = Resource.create(Attributes.of(stringKey("gcp.project_id"), projectId))

    val spanExporter = createSpanExporter(projectId, googleAppId, apiKey)
    val logExporter = createLogExporter(projectId, googleAppId, apiKey)

    // Unify file-mapping initialization and span recovery inline using the trailing lambda API.
    val mmapFileCurrent = File(cacheDir, "crashlytics_otel_current.mmap")
    Log.d("OpenTelemetryManager", "mmap file: $mmapFileCurrent")

    val mutationContext =
      CrashlyticsOtelContext.initialize(mmapFileCurrent.absolutePath, MmapSize.SMALL) {
        recoveredSpans ->
        val recoverPreviousSpans = Firebase.crashlytics.didCrashOnPreviousExecution()
        if (recoverPreviousSpans) {
          Log.d("OpenTelemetryManager", "Found ${recoveredSpans.size} recovered spans to export.")
          scope.launch {
            Log.d(
              "OpenTelemetryManager",
              "Exporting ${recoveredSpans.size} recovered spans: ${recoveredSpans.map { it.name }}",
            )
            spanExporter.export(recoveredSpans.map { it.toSpanData() })
          }
        }
      }

    val crashlyticsSpanProcessor = CrashlyticsSpanProcessor(mutationContext)

    val sdk = createOpenTelemetrySdk(resource, spanExporter, logExporter, crashlyticsSpanProcessor)

    // This wraps it around the Crashlytics delegate.
    // TODO: Document how this won't be necessary after the SpanProcessor interface
    // supports onAttribute.
    CrashlyticsOpenTelemetryDelegate(sdk, crashlyticsSpanProcessor)
  }

  private fun createOpenTelemetrySdk(
    resource: Resource,
    spanExporter: OtlpHttpSpanExporter,
    logExporter: OtlpHttpLogRecordExporter,
    crashlyticsSpanProcessor: CrashlyticsSpanProcessor,
  ): OpenTelemetrySdk {
    val tracerProvider =
      SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(crashlyticsSpanProcessor)
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
        .build()

    val loggerProvider =
      SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(CrashlyticsLogRecordProcessor())
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
        .build()

    return OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .setLoggerProvider(loggerProvider)
      .build()
  }

  private fun getFirebaseApp(): FirebaseApp {
    return try {
      FirebaseApp.getInstance()
    } catch (e: IllegalStateException) {
      throw IllegalStateException(
        "Please add a google-service.json file to the app to configure logs",
        e,
      )
    }
  }

  private fun createSpanExporter(
    projectId: String,
    googleAppId: String,
    apiKey: String,
  ): OtlpHttpSpanExporter {
    val tracesEndpoint =
      "$BASE_ENDPOINT/v1/projects/$projectId/apps/$googleAppId/locations/global/traces"
    return OtlpHttpSpanExporter.builder()
      .setEndpoint(tracesEndpoint)
      .addHeader("X-Goog-Api-Key", apiKey)
      .build()
  }

  private fun createLogExporter(
    projectId: String,
    googleAppId: String,
    apiKey: String,
  ): OtlpHttpLogRecordExporter {
    val logsEndpoint =
      "$BASE_ENDPOINT/v1/projects/$projectId/apps/$googleAppId/locations/global/logs"
    Log.d("OpenTelemetryManager", "Picking telemetry logs endpoint: $logsEndpoint")
    return OtlpHttpLogRecordExporter.builder()
      .setEndpoint(logsEndpoint)
      .addHeader("X-Goog-Api-Key", apiKey)
      .build()
  }
}

// region Helper

/**
 * Sealed class configurations defining strict caps and capacities for memory-mapped allocations.
 */
sealed class MmapConfig {
  abstract val maxSpans: Int
  abstract val maxAttributes: Int

  data class Small(override val maxSpans: Int = 128, override val maxAttributes: Int = 16) :
    MmapConfig()

  data class Medium(override val maxSpans: Int = 1024, override val maxAttributes: Int = 64) :
    MmapConfig()

  data class Large(override val maxSpans: Int = 2048, override val maxAttributes: Int = 128) :
    MmapConfig()
}

/**
 * MmapSize Enum pointing to sealed configurations to cleanly expose limit properties to Kotlin
 * codebases.
 */
enum class MmapSize(val config: MmapConfig) {
  SMALL(MmapConfig.Small()),
  MEDIUM(MmapConfig.Medium()),
  LARGE(MmapConfig.Large()),
}

// endregion
