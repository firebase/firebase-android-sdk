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

import com.google.auth.oauth2.ExternalAccountCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials as GoogleServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
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
    // Disable automatic Google Cloud GCE metadata server detection in unit tests
    environmentVariables.set("NO_GCE_CHECK", "true")
    // Override Google Cloud SDK configuration directory to a non-existent path
    // to prevent the library from naturally loading active local user credentials (via gcloud auth)
    environmentVariables.set("CLOUDSDK_CONFIG", "/non-existent-path")
  }

  @Test
  fun testGetAuthCredential_usesServiceCredentialsJsonPassedIn() {
    val credentialsRetriever = CredentialsRetriever()

    val credential = credentialsRetriever.getAuthCredential(TEST_SERVICE_CREDENTIALS_PATH)!!
    val googleCreds = credential.credentials as GoogleServiceAccountCredentials

    assertEquals("firebase-adminsdk@my-project.iam.gserviceaccount.com", googleCreds.clientEmail)
    assertEquals("abcefg123456789", googleCreds.privateKeyId)
  }

  @Test
  fun testGetAuthCredential_usesWifCredentialsJsonPassedIn() {
    val credentialsRetriever = CredentialsRetriever()

    val credential = credentialsRetriever.getAuthCredential(TEST_WIF_CREDENTIALS_PATH)!!
    val googleCreds = credential.credentials

    assert(googleCreds is ExternalAccountCredentials)
  }

  @Test
  fun testGetAuthCredential_usesEnvFirebaseToken() {
    val expectedRefreshToken = "other-token"
    environmentVariables[AppDistributionEnvironment.ENV_FIREBASE_TOKEN] = expectedRefreshToken
    val credentialsRetriever = CredentialsRetriever()

    val credential = credentialsRetriever.getAuthCredential()!!
    val userCreds = credential.credentials as UserCredentials

    assertEquals(expectedRefreshToken, userCreds.refreshToken)
    assertEquals(RefreshToken.CLIENT_ID, userCreds.clientId)
    assertEquals(RefreshToken.CLIENT_SECRET, userCreds.clientSecret)
  }

  @Test
  fun testGetAuthCredential_usesCachedFirebaseCliToken() {
    environmentVariables["XDG_CONFIG_HOME"] = "src/test/fixtures"
    val credentialsRetriever = CredentialsRetriever()

    val credential = credentialsRetriever.getAuthCredential()!!
    val userCreds = credential.credentials as UserCredentials

    assertEquals("fake-refresh-token", userCreds.refreshToken)
    assertEquals(RefreshToken.CLIENT_ID, userCreds.clientId)
    assertEquals(RefreshToken.CLIENT_SECRET, userCreds.clientSecret)
  }

  @Test
  fun testGetAuthCredential_usesEnvApplicationDefaultCredentials() {
    val testEnvironment: AppDistributionEnvironment = mock()
    // Mock out the Firebase CLI login credentials and force it to return an empty optional. This
    // makes sure the test environment is set up correctly and doesn't fail erroneously on dev
    // machines that have cached CLI credentials.
    whenever(testEnvironment.getFirebaseCliLoginCredentials()).thenReturn(null)
    environmentVariables[AppDistributionEnvironment.ENV_GOOGLE_APPLICATION_CREDENTIALS] =
      TEST_SERVICE_CREDENTIALS_PATH
    val credentialsRetriever = CredentialsRetriever(testEnvironment)

    val credential = credentialsRetriever.getAuthCredential()!!
    val googleCreds = credential.credentials as GoogleServiceAccountCredentials

    assertEquals("firebase-adminsdk@my-project.iam.gserviceaccount.com", googleCreds.clientEmail)
    assertEquals("abcefg123456789", googleCreds.privateKeyId)
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
    environmentVariables[AppDistributionEnvironment.ENV_FIREBASE_TOKEN] = expectedRefreshToken
    val credentialsRetriever = CredentialsRetriever()

    // Also pass in service credentials json directly
    val credential = credentialsRetriever.getAuthCredential(TEST_SERVICE_CREDENTIALS_PATH)!!
    val googleCreds = credential.credentials as GoogleServiceAccountCredentials

    // Verify service credentials auth is configured
    assertEquals("firebase-adminsdk@my-project.iam.gserviceaccount.com", googleCreds.clientEmail)
    assertEquals("abcefg123456789", googleCreds.privateKeyId)
  }

  @Test
  fun testGetAuthCredential_prefersEnvFirebaseTokenOverCachedFirebaseCliToken() {
    // Set env firebase token
    environmentVariables[AppDistributionEnvironment.ENV_FIREBASE_TOKEN] = "env-firebase-token"
    // Also set the cached Firebase CLI token, which is "fake-refresh-token"
    environmentVariables["XDG_CONFIG_HOME"] = "src/test/fixtures"
    val credentialsRetriever = CredentialsRetriever()

    val credential = credentialsRetriever.getAuthCredential()!!
    val userCreds = credential.credentials as UserCredentials

    // Verify access token is the one set as the env firebase token
    assertEquals("env-firebase-token", userCreds.refreshToken)
  }

  @Test
  fun testGetAuthCredential_prefersCachedFirebaseCliTokenOverApplicationDefaultCredentials() {
    // Configure cached Firebase CLI token
    environmentVariables["XDG_CONFIG_HOME"] = "src/test/fixtures"
    // Also configure the application default credentials
    environmentVariables[AppDistributionEnvironment.ENV_GOOGLE_APPLICATION_CREDENTIALS] =
      TEST_SERVICE_CREDENTIALS_PATH
    val credentialsRetriever = CredentialsRetriever()

    val credential = credentialsRetriever.getAuthCredential()!!
    val userCreds = credential.credentials as UserCredentials

    // Verify Firebase CLI token is used, which also verifies that default credentials aren't used
    assertEquals("fake-refresh-token", userCreds.refreshToken)
  }

  @Test
  fun testGetAuthCredential_usesApplicationDefaultCredentialsFallback() {
    val testEnvironment: AppDistributionEnvironment = mock()
    whenever(testEnvironment.getFirebaseCliLoginCredentials()).thenReturn(null)

    val mockAdcCredentials: GoogleCredentials = mock()
    whenever(mockAdcCredentials.createScoped(ApiEndpoints.SCOPES)).thenReturn(mockAdcCredentials)

    val credentialsRetriever =
      CredentialsRetriever(
        appDistributionEnvironment = testEnvironment,
        adcCredentialsProvider = { mockAdcCredentials }
      )

    val credential = credentialsRetriever.getAuthCredential()!!
    assertEquals(mockAdcCredentials, credential.credentials)
  }

  companion object {
    private val TEST_SERVICE_CREDENTIALS_PATH =
      FixtureUtils.getFixtureAsFile("test-service-credentials.json").absolutePath
    private val TEST_WIF_CREDENTIALS_PATH =
      FixtureUtils.getFixtureAsFile("test-wif-credentials.json").absolutePath
  }
}
