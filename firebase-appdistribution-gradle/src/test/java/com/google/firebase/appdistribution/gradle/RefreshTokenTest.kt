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

import com.google.api.client.http.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RefreshTokenTest {
  private var httpTransport: HttpTransport =
    SuccessWithContent("{\"access_token\":\"access-token\"}")

  @Test
  fun testGenerateCredentialFrom() {
    val refreshToken = "test-fresh-token"
    val expectedAccessToken = "access-token"
    val token = RefreshToken(refreshToken, httpTransport)
    val credentials = token.generateNewCredentials()
    assertNotNull(credentials)
    assertEquals(expectedAccessToken, credentials.accessToken)
  }
}
