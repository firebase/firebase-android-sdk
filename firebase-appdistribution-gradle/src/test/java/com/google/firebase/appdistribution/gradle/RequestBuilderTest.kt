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
import com.google.api.client.http.HttpRequest
import com.google.api.client.testing.http.MockHttpContent
import com.google.api.client.testing.http.MockHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBuilderTest {
  private val mockHttpTransport = MockHttpTransport()
  private var request: HttpRequest =
    mockHttpTransport.createRequestFactory().buildGetRequest(GenericUrl("http://foo.bar"))

  @Test
  fun testSetContent() {
    val content = MockHttpContent()
    content.setContent("foo".toByteArray())

    val builder = RequestBuilder(request)
    builder.setContent(content)

    assertEquals(request.content, content)
  }

  @Test
  fun testExecute() {
    val builder = RequestBuilder(request)

    val response = builder.execute()

    assertTrue(response.isSuccessStatusCode)
  }

  @Test
  fun testExecute_DefaultsToUnknownUserAgent() {
    val builder = RequestBuilder(request)

    val response = builder.execute()

    val req = response.request
    val userAgent = req.headers.userAgent
    val clientVersion = req.headers.getFirstHeaderStringValue(RequestBuilder.X_CLIENT_VERSION)
    assertTrue(req.suppressUserAgentSuffix)
    assertEquals("Firebase App Distro Client/unknown_version", userAgent)
    assertEquals("gradle/unknown_version", clientVersion)
  }
}
