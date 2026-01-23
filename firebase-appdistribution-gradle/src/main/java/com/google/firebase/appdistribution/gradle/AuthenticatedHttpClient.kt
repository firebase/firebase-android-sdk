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

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.GoogleUtils
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpContent
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.GeneralSecurityException

class AuthenticatedHttpClient
private constructor(
  // This is thread safe and should be shared between requests.
  private val httpRequestFactory: HttpRequestFactory,
) {
  constructor(
    credential: Credential
  ) : this(newGoogleHttpTransport().createRequestFactory(credential))

  constructor(httpTransport: HttpTransport) : this(httpTransport.createRequestFactory())

  fun newGetRequest(url: GenericUrl) = RequestBuilder(httpRequestFactory.buildGetRequest(url))

  @JvmOverloads
  fun newPostRequest(url: GenericUrl, content: HttpContent? = null) =
    RequestBuilder(httpRequestFactory.buildPostRequest(url, content))

  fun newPatchRequest(url: GenericUrl, content: HttpContent? = null): RequestBuilder =
    RequestBuilder(httpRequestFactory.buildPostRequest(url, content))
      .setHeader("X-Http-Method-Override", "PATCH")

  companion object {
    @Throws(IOException::class, GeneralSecurityException::class)
    fun newGoogleHttpTransport(): HttpTransport {
      val builder = NetHttpTransport.Builder()

      // Trust Google cert
      builder.trustCertificates(GoogleUtils.getCertificateTrustStore())

      // Trust customer CA cert, if exists
      val trustStorePath = System.getProperty("javax.net.ssl.trustStore")
      val trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword")
      if (trustStorePath != null && trustStorePassword != null) {
        builder.trustCertificatesFromJavaKeyStore(
          Files.newInputStream(Paths.get(trustStorePath)),
          trustStorePassword
        )
      }
      return builder.build()
    }
  }
}
