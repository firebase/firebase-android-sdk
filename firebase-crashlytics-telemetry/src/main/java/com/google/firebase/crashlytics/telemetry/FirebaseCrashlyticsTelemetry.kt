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

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD
import io.opentelemetry.semconv.UrlAttributes.URL_FULL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/** Main entry point for Firebase Crashlytics Telemetry SDK. */
public class FirebaseCrashlyticsTelemetry
internal constructor(private val openTelemetry: OpenTelemetry) {
  internal val eventEmitter: EventEmitter = EventEmitter(openTelemetry)
  @Volatile private var activeScreen: Attributes = Attributes.empty()

  /**
   * Records a screen navigation event.
   *
   * @param screenName The screen name.
   */
  public fun logScreenAppear(screenName: String) {
    activeScreen = Attributes.of(SCREEN_NAME, screenName)
    eventEmitter.emit(
      TelemetryEvent.SCREEN_VIEW,
      Attributes.of(NAVIGATION_DESTINATION_NAME, screenName, SCREEN_NAME, screenName),
    )
  }

  /**
   * Records a screen navigation event.
   *
   * @param screenName The screen name.
   * @param screenClass The class name or route identifier.
   */
  public fun logScreenAppear(screenName: String, screenClass: String) {
    activeScreen = Attributes.of(SCREEN_NAME, screenName, SCREEN_ID, screenClass)
    eventEmitter.emit(
      TelemetryEvent.SCREEN_VIEW,
      Attributes.of(
        NAVIGATION_DESTINATION_NAME,
        screenName,
        SCREEN_NAME,
        screenName,
        SCREEN_ID,
        screenClass,
      ),
    )
  }

  public fun getTracer(instrumentationScopeName: String): Tracer {
    return openTelemetry.getTracer(instrumentationScopeName)
  }

  public fun getTracer(
    instrumentationScopeName: String,
    instrumentationScopeVersion: String,
  ): Tracer {
    return openTelemetry.getTracer(instrumentationScopeName, instrumentationScopeVersion)
  }

  /**
   * Records a widget click event.
   *
   * @param widgetName The widget name or identifier.
   */
  public fun recordClick(widgetName: String) {
    eventEmitter.emit(
      TelemetryEvent.CLICK,
      Attributes.of(WIDGET_ID, widgetName, WIDGET_NAME, widgetName)
        .toBuilder()
        .putAll(activeScreen)
        .build(),
    )
  }

  /**
   * Records a widget click event.
   *
   * @param widgetName The widget name or identifier.
   * @param x Horizontal screen coordinate, in pixels.
   * @param y Vertical screen coordinate, in pixels.
   */
  public fun recordClick(widgetName: String, x: Int, y: Int) {
    eventEmitter.emit(
      TelemetryEvent.CLICK,
      Attributes.of(
          WIDGET_ID,
          widgetName,
          WIDGET_NAME,
          widgetName,
          SCREEN_COORDINATE_X,
          x.toLong(),
          SCREEN_COORDINATE_Y,
          y.toLong(),
        )
        .toBuilder()
        .putAll(activeScreen)
        .build(),
    )
  }

  /**
   * Executes a network operation inside a root span. Spans started inside [block], such as OkHttp
   * requests, become its children.
   *
   * Exceptions thrown by [block] mark the request as failed and are rethrown. Cancellation is not
   * recorded as a failure.
   *
   * @param url The request URL.
   * @param method The HTTP method.
   * @param name The span name. Defaults to [method].
   * @param block The network operation.
   * @return The result of [block].
   */
  public suspend fun <T> networkRequest(
    url: String,
    method: String = "GET",
    name: String? = null,
    block: suspend NetworkRequestContext.() -> T,
  ): T {
    val span =
      getTracer(EventEmitter.INSTRUMENTATION_SCOPE_NAME)
        .spanBuilder(name ?: method)
        .setNoParent()
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(HTTP_REQUEST_METHOD, method)
        .setAttribute(URL_FULL, url)
        .startSpan()
    val context = NetworkRequestContext(url, method, span)
    try {
      return withContext(Context.current().with(span).asContextElement()) { context.block() }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      context.recordError(e)
      throw e
    } finally {
      context.recordResponseCode()
      span.end()
    }
  }

  public companion object {
    private val NAVIGATION_DESTINATION_NAME: AttributeKey<String> =
      stringKey("app.navigation.destination.name")
    private val SCREEN_NAME: AttributeKey<String> = stringKey("app.screen.name")
    private val SCREEN_ID: AttributeKey<String> = stringKey("app.screen.id")
    private val WIDGET_ID: AttributeKey<String> = stringKey("app.widget.id")
    private val WIDGET_NAME: AttributeKey<String> = stringKey("app.widget.name")
    private val SCREEN_COORDINATE_X: AttributeKey<Long> = longKey("app.screen.coordinate.x")
    private val SCREEN_COORDINATE_Y: AttributeKey<Long> = longKey("app.screen.coordinate.y")

    /** The [FirebaseCrashlyticsTelemetry] instance for the default FirebaseApp. */
    @JvmStatic
    public val instance: FirebaseCrashlyticsTelemetry
      get() = Firebase.app[FirebaseCrashlyticsTelemetry::class.java]
  }
}

/** Access the FirebaseCrashlyticsTelemetry instance for the default FirebaseApp. */
public val Firebase.telemetry: FirebaseCrashlyticsTelemetry
  get() = FirebaseCrashlyticsTelemetry.instance

/** Access the FirebaseCrashlyticsTelemetry instance from FirebaseCrashlytics. */
public val FirebaseCrashlytics.telemetry: FirebaseCrashlyticsTelemetry
  get() = FirebaseCrashlyticsTelemetry.instance
