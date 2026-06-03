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

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials

/**
 * This class encapsulates a refresh token that can be used for generate a new
 * [HttpCredentialsAdapter] to be used when authenticating requests. By using the refresh token that
 * is returned after the oauth flow, we can generate new access tokens for future requests.
 */
class RefreshToken(private val refreshToken: String) {
  /**
   * Using the provided refresh token, this makes a request to generate a new access token that can
   * be used to authenticate requests
   *
   * @return the credentials that were generated from the refresh token.
   */
  fun generateNewCredentials(): HttpCredentialsAdapter {
    return HttpCredentialsAdapter(
      UserCredentials.newBuilder()
        .setClientId(CLIENT_ID)
        .setClientSecret(CLIENT_SECRET)
        .setRefreshToken(refreshToken)
        .build()
    )
  }

  companion object {
    // Firebase CLI ClientID and ClientSecret
    // In this type of application, the client secret is not treated as a secret.
    // See: https://developers.google.com/identity/protocols/OAuth2InstalledApp
    // TODO: Use separate ID's/secrets for each tool
    internal const val CLIENT_ID =
      "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com"
    internal const val CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi"
  }
}
