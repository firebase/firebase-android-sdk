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

import com.google.api.client.http.HttpContent
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import java.io.IOException

/**
 * This class is responsible for wrapping a [HttpRequest] to provide convenience methods for the
 * different request types we need to make. It is is also responsible for adding the User-Agent for
 * each of our requests.
 */
class RequestBuilder internal constructor(private val request: HttpRequest) {

  /**
   * Sets the content for a request.
   *
   * @param content The content for the requests
   * @return The current builder instance
   */
  fun setContent(content: HttpContent?): RequestBuilder {
    request.setContent(content)
    return this
  }

  /**
   * Set header value for a request
   *
   * @param headerName The header name
   * @param value The header value
   * @return The current builder instance
   */
  fun setHeader(headerName: String?, value: String?): RequestBuilder {
    request.headers[headerName] = value
    return this
  }

  /**
   * Executes an http requests based on the configuration that has been done thus-fa.
   *
   * @return The response from the requests resource
   * @throws IOException if there is a problem executing the request
   */
  fun execute(): HttpResponse {
    addVersionHeaders(request.headers)

    // Suppresses the user agent suffix added by the http library
    request.setSuppressUserAgentSuffix(true)
    return request.execute()
  }

  private fun addVersionHeaders(headers: HttpHeaders) {
    val version =
      AppDistributionPlugin::class.java.getPackage().implementationVersion ?: CLIENT_VERSION_UNKNOWN
    headers.setUserAgent("Firebase App Distro Client/$version")
    headers[X_CLIENT_VERSION] = "gradle/$version"
  }

  companion object {
    const val X_CLIENT_VERSION = "X-Client-Version"
    private const val CLIENT_VERSION_UNKNOWN = "unknown_version"
  }
}
