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
import com.google.common.collect.ImmutableList
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_PROJECT_NUMBER
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_TESTER_EMAILS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class TesterManagementOptionsTest {
  private val mockCredentialsRetriever: CredentialsRetriever = mock()
  private val mockCredential: Credential = mock()

  @Test
  fun testValidateAndBuild_successfullyBuildsOptions() {
    mockSuccessfulCredential()

    val options =
      TesterManagementOptions(
        projectNumber = 123L,
        credentialsRetriever = mockCredentialsRetriever,
        emailsValue = "1@e.mail,2@e.mail",
        serviceCredentialsFile = MOCK_CREDENTIALS_JSON_PATH
      )

    assertEquals(options.projectNumber, 123L)
    assertEquals(options.emails, ImmutableList.of("1@e.mail", "2@e.mail"))
    assertEquals(options.credential, mockCredential)
  }

  @Test
  fun testValidateAndBuild_prefersEmailsOverEmailsFile() {
    mockSuccessfulCredential()

    val options =
      TesterManagementOptions(
        projectNumber = 123L,
        credentialsRetriever = mockCredentialsRetriever,
        emailsValue = "list1@e.mail,list2@e.mail",
        emailsFile = "src/test/fixtures/emails.txt",
        serviceCredentialsFile = MOCK_CREDENTIALS_JSON_PATH
      )

    assertEquals(options.emails, ImmutableList.of("list1@e.mail", "list2@e.mail"))
  }

  @Test
  fun testValidateAndBuild_parsesEmailsFromEmailFile() {
    mockSuccessfulCredential()

    val options =
      TesterManagementOptions(
        projectNumber = 123L,
        credentialsRetriever = mockCredentialsRetriever,
        emailsFile = "src/test/fixtures/emails.txt",
        serviceCredentialsFile = MOCK_CREDENTIALS_JSON_PATH
      )

    // Emails from the fixture emails.txt
    assertEquals(options.emails, ImmutableList.of("file1@e.mail", "file2@e.mail", "file3@e.mail"))
  }

  @Test
  fun testValidateAndBuild_throwsIfEmailsNotSet() {
    mockSuccessfulCredential()

    val e =
      assertThrows(AppDistributionException::class.java) {
        TesterManagementOptions(
          projectNumber = 123L,
          credentialsRetriever = mockCredentialsRetriever,
          serviceCredentialsFile = MOCK_CREDENTIALS_JSON_PATH
        )
      }

    assertEquals(MISSING_TESTER_EMAILS, e.reason)
  }

  @Test
  fun testValidateAndBuild_throwsIfEmailsAreEmpty() {
    mockSuccessfulCredential()

    val e =
      assertThrows(AppDistributionException::class.java) {
        TesterManagementOptions(
          projectNumber = 123L,
          credentialsRetriever = mockCredentialsRetriever,
          emailsValue = "",
          serviceCredentialsFile = MOCK_CREDENTIALS_JSON_PATH
        )
      }

    assertEquals(MISSING_TESTER_EMAILS, e.reason)
  }

  @Test
  fun testValidateAndBuild_throwsIfProjectNumberIsInvalid() {
    mockSuccessfulCredential()

    val e =
      assertThrows(AppDistributionException::class.java) {
        TesterManagementOptions(
          projectNumber = -1,
          credentialsRetriever = mockCredentialsRetriever,
          emailsValue = "1@e.mail,2@e.mail",
          serviceCredentialsFile = MOCK_CREDENTIALS_JSON_PATH
        )
      }

    assertEquals(MISSING_PROJECT_NUMBER, e.reason)
  }

  // Mock out the credentials retriever call to return a fake successful credential
  private fun mockSuccessfulCredential() =
    whenever(mockCredentialsRetriever.getAuthCredential(MOCK_CREDENTIALS_JSON_PATH))
      .thenReturn(mockCredential)

  companion object {
    const val MOCK_CREDENTIALS_JSON_PATH = "service-credentials.json"
  }
}
