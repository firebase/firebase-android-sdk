/*
 * Copyright 2024 Google LLC
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

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import java.io.File
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UploadServiceTest {
  private val mockHttpTransport: MockHttpTransport = HttpTransportWithEtag()
  private val httpClient = AuthenticatedHttpClient(mockHttpTransport)
  private lateinit var apk: File

  @Before
  fun setUp() {
    apk = FixtureUtils.getFixtureAsFile("test.apk")
  }

  @Test
  fun testUploadDistribution() {
    val service = UploadService(httpClient)
    val uploadResponse = service.uploadDistribution(APP_NAME, apk)
    assertNotNull(uploadResponse)
    assertEquals(OPERATION_NAME, uploadResponse)
  }

  private inner class HttpTransportWithEtag : MockHttpTransport() {
    override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
      return object : MockLowLevelHttpRequest() {
        override fun execute(): LowLevelHttpResponse {
          val response = MockLowLevelHttpResponse()
          response.setContent("{ \"name\" : \"${OPERATION_NAME}\" }")
          response.setStatusCode(200)
          return response
        }
      }
    }
  }

  companion object {
    private const val APP_NAME = "projects/123/apps/1:123:android:abc"
    private const val OPERATION_NAME = "${APP_NAME}/releases/-/operations/release-hash"
  }
}
