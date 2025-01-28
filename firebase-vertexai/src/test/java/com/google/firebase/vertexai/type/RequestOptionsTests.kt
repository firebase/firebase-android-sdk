/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.vertexai.type

import com.google.firebase.vertexai.type.RequestOptions.Companion.DEFAULT_API_VERSION
import com.google.firebase.vertexai.type.RequestOptions.Companion.DEFAULT_ENDPOINT
import com.google.firebase.vertexai.type.RequestOptions.Companion.DEFAULT_TIMEOUT_IN_MILLIS
import io.kotest.matchers.equals.shouldBeEqual
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.junit.Test

internal class RequestOptionsTests {
  private val defaultTimeout = DEFAULT_TIMEOUT_IN_MILLIS.toDuration(DurationUnit.MILLISECONDS)

  @Test
  fun `init default values`() {
    val requestOptions = RequestOptions()

    requestOptions.timeout shouldBeEqual defaultTimeout
    requestOptions.endpoint shouldBeEqual DEFAULT_ENDPOINT
    requestOptions.apiVersion shouldBeEqual DEFAULT_API_VERSION.value
  }

  @Test
  fun `init custom timeout`() {
    val expectedTimeoutInMillis = 60_000L

    val requestOptions = RequestOptions(timeoutInMillis = expectedTimeoutInMillis)

    requestOptions.timeout shouldBeEqual
      expectedTimeoutInMillis.toDuration(DurationUnit.MILLISECONDS)
    requestOptions.endpoint shouldBeEqual DEFAULT_ENDPOINT
    requestOptions.apiVersion shouldBeEqual DEFAULT_API_VERSION.value
  }

  @Test
  fun `init API version v1`() {
    val expectedApiVersion = ApiVersion.V1

    val requestOptions = RequestOptions(apiVersion = expectedApiVersion)

    requestOptions.timeout shouldBeEqual defaultTimeout
    requestOptions.endpoint shouldBeEqual DEFAULT_ENDPOINT
    requestOptions.apiVersion shouldBeEqual expectedApiVersion.value
  }

  @Test
  fun `init API version v1beta`() {
    val expectedApiVersion = ApiVersion.V1BETA

    val requestOptions = RequestOptions(apiVersion = expectedApiVersion)

    requestOptions.timeout shouldBeEqual defaultTimeout
    requestOptions.endpoint shouldBeEqual DEFAULT_ENDPOINT
    requestOptions.apiVersion shouldBeEqual expectedApiVersion.value
  }

  @Test
  fun `init all public options`() {
    val expectedTimeoutInMillis = 30_000L
    val expectedApiVersion = ApiVersion.V1BETA

    val requestOptions =
      RequestOptions(timeoutInMillis = expectedTimeoutInMillis, apiVersion = expectedApiVersion)

    requestOptions.timeout shouldBeEqual
      expectedTimeoutInMillis.toDuration(DurationUnit.MILLISECONDS)
    requestOptions.endpoint shouldBeEqual DEFAULT_ENDPOINT
    requestOptions.apiVersion shouldBeEqual expectedApiVersion.value
  }
}
