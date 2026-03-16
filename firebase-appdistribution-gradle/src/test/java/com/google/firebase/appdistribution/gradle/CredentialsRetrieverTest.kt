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
import com.google.api.client.http.HttpTransport
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlin.test.assertContains
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class CredentialsRetrieverTest {
  @get:Rule val environmentVariables = EnvironmentVariables()

  @Before
  fun unsetFirebaseToken() {
    environmentVariables.clear(
      AppDistributionEnvironment.ENV_FIREBASE_TOKEN,
      "XDG_CONFIG_HOME",
      AppDistributionEnvironment.ENV_GOOGLE_APPLICATION_CREDENTIALS
    )
  }

  @Test
  fun testGetAuthCredential_usesServiceCredentialsJsonPassedIn() {
    val credentialsRetriever = CredentialsRetriever()

    val credential = getAuthCredential(credentialsRetriever, TEST_SERVICE_CREDENTIALS_PATH)

    assertEquals(
      "firebase-adminsdk@my-project.iam.gserviceaccount.com",
      credential.serviceAccountId
    )
    assertEquals("abcefg123456789", credential.serviceAccountPrivateKeyId)
  }

  @Test
  fun testGetAuthCredential_usesEnvFirebaseToken() {
    val expectedRefreshToken = "other-token"
    val transport: HttpTransport =
      MockVerifyRefreshToken(expectedRefreshToken, "{\"access_token\":\"some-access-token\"}")
    environmentVariables[AppDistributionEnvironment.ENV_FIREBASE_TOKEN] = expectedRefreshToken
    val credentialsRetriever = CredentialsRetriever(transport)

    val credential = getAuthCredential(credentialsRetriever)

    assertEquals("some-access-token", credential.accessToken)
  }

  @Test
  fun testGetAuthCredential_usesCachedFirebaseCliToken() {
    val transport: HttpTransport =
      MockVerifyRefreshToken("fake-refresh-token", "{\"access_token\":\"some-access-token\"}")
    environmentVariables["XDG_CONFIG_HOME"] = "src/test/fixtures"
    val credentialsRetriever = CredentialsRetriever(transport)

    val credential = getAuthCredential(credentialsRetriever)

    assertContains("some-access-token", credential.accessToken)
  }

  @Test
  fun testGetAuthCredential_usesEnvApplicationDefaultCredentials() {
    val testEnvironment: AppDistributionEnvironment = mock()
    // Mock out the Firebase CLI login credentials and force it to return an empty optional. This
    // makes sure the test environment is set up correctly and doesn't fail erroneously on dev
    // machines that have cached CLI credentials.
    whenever(testEnvironment.getFirebaseCliLoginCredentials(any())).thenReturn(null)
    environmentVariables[AppDistributionEnvironment.ENV_GOOGLE_APPLICATION_CREDENTIALS] =
      TEST_SERVICE_CREDENTIALS_PATH
    val credentialsRetriever = CredentialsRetriever(testEnvironment)

    val credential = getAuthCredential(credentialsRetriever)

    assertEquals(
      "firebase-adminsdk@my-project.iam.gserviceaccount.com",
      credential.serviceAccountId
    )
    assertEquals("abcefg123456789", credential.serviceAccountPrivateKeyId)
  }

  @Test
  fun testGetAuthCredential_returnsNullIfNoValidCredentials() {
    val testEnvironment: AppDistributionEnvironment = mock()
    val credentialsRetriever = CredentialsRetriever(testEnvironment)
    val credentials = credentialsRetriever.getAuthCredential(null)
    assertNull(credentials)
  }

  @Test
  fun testGetAuthCredential_prefersServiceCredentialsJsonOverEnvFirebaseToken() {
    // Configure mock firebase token
    val expectedRefreshToken = "other-token"
    val transport: HttpTransport =
      MockVerifyRefreshToken(expectedRefreshToken, "{\"access_token\":\"some-access-token\"}")
    environmentVariables[AppDistributionEnvironment.ENV_FIREBASE_TOKEN] = expectedRefreshToken
    val credentialsRetriever = CredentialsRetriever(transport)

    // Also pass in service credentials json directly
    val credential = getAuthCredential(credentialsRetriever, TEST_SERVICE_CREDENTIALS_PATH)

    // Verify service credentials auth is configured
    assertEquals(
      "firebase-adminsdk@my-project.iam.gserviceaccount.com",
      credential.serviceAccountId
    )
    assertEquals("abcefg123456789", credential.serviceAccountPrivateKeyId)

    // Verify the firebase refresh token was not used
    assertNull(credential.accessToken)
  }

  @Test
  fun testGetAuthCredential_prefersEnvFirebaseTokenOverCachedFirebaseCliToken() {
    // Set env firebase token
    environmentVariables[AppDistributionEnvironment.ENV_FIREBASE_TOKEN] = "env-firebase-token"
    // Also set the cached Firebase CLI token, which is "fake-refresh-token"
    environmentVariables["XDG_CONFIG_HOME"] = "src/test/fixtures"
    // Configure transport for the env firebase token option
    val transport: HttpTransport =
      MockVerifyRefreshToken(
        "env-firebase-token",
        "{\"access_token\":\"access-token-for-env-firebase-token\"}"
      )
    val credentialsRetriever = CredentialsRetriever(transport)

    val credential = getAuthCredential(credentialsRetriever)

    // Verify access token is the one set as the env firebase token
    assertEquals("access-token-for-env-firebase-token", credential.accessToken)
  }

  @Test
  fun testGetAuthCredential_prefersCachedFirebaseCliTokenOverApplicationDefaultCredentials() {
    // Configure cached Firebase CLI token
    val transport: HttpTransport =
      MockVerifyRefreshToken("fake-refresh-token", "{\"access_token\":\"some-access-token\"}")
    environmentVariables["XDG_CONFIG_HOME"] = "src/test/fixtures"
    val credentialsRetriever = CredentialsRetriever(transport)
    // Also configure the application default credentials
    environmentVariables[AppDistributionEnvironment.ENV_GOOGLE_APPLICATION_CREDENTIALS] =
      TEST_SERVICE_CREDENTIALS_PATH

    val credential = getAuthCredential(credentialsRetriever)

    // Verify Firebase CLI token is used, which also verifies that default credentials aren't used
    // since it's not of type ServiceAccountCredentials
    assertEquals("some-access-token", credential.accessToken)
  }

  private fun getAuthCredential(
    credentialsRetriever: CredentialsRetriever,
    serviceCredentialsPath: String? = null
  ) = credentialsRetriever.getAuthCredential(serviceCredentialsPath) as GoogleCredential

  companion object {
    private val TEST_SERVICE_CREDENTIALS_PATH =
      FixtureUtils.getFixtureAsFile("test-service-credentials.json").absolutePath
  }
}
