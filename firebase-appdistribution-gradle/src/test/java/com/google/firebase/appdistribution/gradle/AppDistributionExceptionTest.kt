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

package com.google.firebase.appdistribution.gradle

import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.Test

class AppDistributionExceptionTest {

  @Test
  fun fromHttpResponseException_withMessageField_parsesCorrectly() {
    val errorResponse =
      "{\"error\":{\"code\":404,\"message\":\"The GCS bucket 'my-bucket' was not found\",\"status\":\"NOT_FOUND\"}}"
    val httpResponseException = createHttpResponseException(404, errorResponse)

    val exception =
      AppDistributionException.fromHttpResponseException(
        AppDistributionException.Reason.STARTING_TEST_FAILED,
        httpResponseException
      )

    assertEquals(AppDistributionException.Reason.STARTING_TEST_FAILED, exception.reason)
    assertContains(exception.message!!, "The GCS bucket 'my-bucket' was not found")
  }

  @Test
  fun fromHttpResponseException_withLocalizedMessageDetail_parsesCorrectly() {
    val errorResponse =
      "{\"error\":{\"code\":404,\"message\":\"Resource not found\",\"status\":\"NOT_FOUND\"," +
        "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.LocalizedMessage\",\"message\":\"User-facing custom error message\"}]}}"
    val httpResponseException = createHttpResponseException(404, errorResponse)

    val exception =
      AppDistributionException.fromHttpResponseException(
        AppDistributionException.Reason.STARTING_TEST_FAILED,
        httpResponseException
      )

    assertEquals(AppDistributionException.Reason.STARTING_TEST_FAILED, exception.reason)
    assertContains(exception.message!!, "User-facing custom error message")
  }

  @Test
  fun fromHttpResponseException_withGenericResponse_fallsBackToStatusMessage() {
    val httpResponseException = createHttpResponseException(404, "")

    val exception =
      AppDistributionException.fromHttpResponseException(
        AppDistributionException.Reason.STARTING_TEST_FAILED,
        httpResponseException
      )

    assertEquals(AppDistributionException.Reason.STARTING_TEST_FAILED, exception.reason)
    assertContains(exception.message!!, "Not Found")
  }

  private fun createHttpResponseException(statusCode: Int, content: String): HttpResponseException {
    val statusMessage = if (statusCode == 404) "Not Found" else "OK"
    return HttpResponseException.Builder(statusCode, statusMessage, HttpHeaders())
      .setContent(content)
      .build()
  }
}
