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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.io.IOException

/**
 * This class encapsulates a refresh token that can be used for generate a new [GoogleCredential] to
 * be used when authenticating requests. By using the refresh token the is returned after the oauth
 * flow, we can generate new access tokens for future requests.
 */
class RefreshToken(private val refreshToken: String, private val transport: HttpTransport) {
  /**
   * Using the provided refresh token, this makes a request to generate a new access token that can
   * be used to authenticate requests
   *
   * @return the credentials that were generated from the refresh token. Its worth noting that these
   * credentials do not include the refresh token, only a new access token
   * @throws IOException thrown if there was a problem making a request to the oauth provider
   */
  fun generateNewCredentials(): GoogleCredential {
    val response =
      GoogleRefreshTokenRequest(
          transport,
          GsonFactory.getDefaultInstance(),
          refreshToken,
          CLIENT_ID,
          CLIENT_SECRET
        )
        .execute()

    val credential = GoogleCredential.Builder().build()
    credential.setFromTokenResponse(response)

    return credential
  }

  companion object {
    // Firebase CLI ClientID and ClientSecret
    // In this type of application, the client secret is not treated as a secret.
    // See: https://developers.google.com/identity/protocols/OAuth2InstalledApp
    // TODO: Use separate ID's/secrets for each tool
    private const val CLIENT_ID =
      "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi"
  }
}
