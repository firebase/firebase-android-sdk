/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import android.util.Log
import com.google.android.datatransport.Encoding
import com.google.android.datatransport.Event
import com.google.android.datatransport.TransportFactory
import com.google.firebase.inject.Provider

/**
 * The [EventGDTLoggerInterface] is for testing purposes so that we can mock EventGDTLogger in other
 * classes that depend on it.
 *
 * @hide
 */
internal fun interface EventGDTLoggerInterface {
  fun log(sessionEvent: SessionEvent)
}

/**
 * The [EventGDTLogger] is responsible for encoding and logging events to the Google Data Transport
 * library.
 *
 * @hide
 */
internal class EventGDTLogger(private val transportFactoryProvider: Provider<TransportFactory>) :
  EventGDTLoggerInterface {

  // Logs a [SessionEvent] to FireLog
  override fun log(sessionEvent: SessionEvent) {
    transportFactoryProvider
      .get()
      .getTransport(
        AQS_LOG_SOURCE,
        SessionEvent::class.java,
        Encoding.of("json"),
        this::encode,
      )
      .send(Event.ofData(sessionEvent))
  }

  private fun encode(value: SessionEvent): ByteArray {
    val jsonEvent = SessionEvents.SESSION_EVENT_ENCODER.encode(value)
    Log.d(TAG, "Session Event: $jsonEvent")
    return jsonEvent.toByteArray()
  }

  companion object {
    private const val TAG = "EventGDTLogger"

    private const val AQS_LOG_SOURCE = "FIREBASE_APPQUALITY_SESSION"
  }
}
