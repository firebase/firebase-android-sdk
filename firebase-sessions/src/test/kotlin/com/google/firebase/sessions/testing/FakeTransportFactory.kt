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

package com.google.firebase.sessions.testing

import com.google.android.datatransport.*
import com.google.firebase.sessions.SessionEvent

/** Fake [Transport] that implements [send]. */
internal class FakeTransport<T>() : Transport<T> {

  var sentEvent: Event<T>? = null

  override fun send(event: Event<T>?) {
    this.sentEvent = event
  }

  override fun schedule(p0: Event<T>?, p1: TransportScheduleCallback?) {
    return
  }
}

/** Fake [TransportFactory] that implements [getTransport]. */
internal class FakeTransportFactory() : TransportFactory {

  var name: String? = null
  var payloadEncoding: Encoding? = null
  var fakeTransport: FakeTransport<SessionEvent>? = null

  override fun <T> getTransport(
    name: String?,
    payloadType: java.lang.Class<T>?,
    payloadEncoding: Encoding?,
    payloadTransformer: Transformer<T, ByteArray?>?
  ): Transport<T>? {
    this.name = name
    this.payloadEncoding = payloadEncoding
    val fakeTransport = FakeTransport<T>()
    @Suppress("UNCHECKED_CAST")
    this.fakeTransport = fakeTransport as FakeTransport<SessionEvent>
    return fakeTransport
  }

  @Deprecated("This is deprecated in the API. Don't use or expect on this function.")
  override fun <T> getTransport(
    name: String?,
    payloadType: java.lang.Class<T>?,
    payloadTransformer: Transformer<T, ByteArray?>?
  ): Transport<T>? {
    return null
  }
}
