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

import com.google.api.client.http.GenericUrl
import com.google.api.client.testing.http.MockHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticatedHttpClientTest {
  private val mockTransport = MockHttpTransport()
  private val authenticatedHttpClient = AuthenticatedHttpClient(mockTransport)

  @Test
  fun testNewGetRequest() {
    val url = GenericUrl("http://foo.bar")
    val builder = authenticatedHttpClient.newGetRequest(url)
    val response = builder.execute()
    val request = response.request
    assertEquals(url, request.url)
    assertEquals("GET", request.requestMethod)
  }

  @Test
  fun testNewPostRequest() {
    val url = GenericUrl("http://foo.bar")
    val builder = authenticatedHttpClient.newPostRequest(url)
    val response = builder.execute()
    val request = response.request
    assertEquals(url, request.url)
    assertEquals("POST", request.requestMethod)
  }
}
