// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.functions

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** An internal class for keeping track of options applied to an HttpsCallableReference. */
internal class HttpsCallOptions {
  // The timeout to use for calls from references created by this Functions.
  private var timeout = DEFAULT_TIMEOUT
  private var timeoutUnits = DEFAULT_TIMEOUT_UNITS
  @JvmField public val limitedUseAppCheckTokens: Boolean
  @JvmField val headers: MutableMap<String, String>

  /** Creates an (internal) HttpsCallOptions from the (external) [HttpsCallableOptions]. */
  internal constructor(publicCallableOptions: HttpsCallableOptions) {
    limitedUseAppCheckTokens = publicCallableOptions.limitedUseAppCheckTokens
    headers = publicCallableOptions.headers.toMutableMap()
  }

  internal constructor() {
    limitedUseAppCheckTokens = false
    headers = mutableMapOf()
  }

  internal fun getLimitedUseAppCheckTokens(): Boolean {
    return limitedUseAppCheckTokens
  }

  /**
   * Changes the timeout for calls from this instance of Functions. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  internal fun setTimeout(timeout: Long, units: TimeUnit) {
    this.timeout = timeout
    timeoutUnits = units
  }

  /**
   * Returns the timeout for calls from this instance of Functions.
   *
   * @return The timeout, in milliseconds.
   */
  internal fun getTimeout(): Long {
    return timeoutUnits.toMillis(timeout)
  }

  /**
   * Adds an HTTP header for calls from this instance of Functions.
   *
   * Note that an existing header with the same name will be overwritten.
   *
   * @param name Name of HTTP header
   * @param value Value of HTTP header
   */
  internal fun addHeader(name: String, value: String): HttpsCallOptions {
    headers[name] = value
    return this
  }

  /**
   * Adds all HTTP headers of passed map for calls from this instance of Functions.
   *
   * Note that an existing header with the same name will be overwritten.
   *
   * @param headers Map of HTTP headers (name to value)
   */
  internal fun addHeaders(headers: Map<String, String>): HttpsCallOptions {
    this.headers.putAll(headers)
    return this
  }

  /** Creates a new OkHttpClient with these options applied to it. */
  internal fun apply(client: OkHttpClient): OkHttpClient {
    return client
      .newBuilder()
      .callTimeout(timeout, timeoutUnits)
      .readTimeout(timeout, timeoutUnits)
      .build()
  }

  private companion object {
    // The default timeout to use for all calls.
    private const val DEFAULT_TIMEOUT: Long = 70
    private val DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS
  }
}
