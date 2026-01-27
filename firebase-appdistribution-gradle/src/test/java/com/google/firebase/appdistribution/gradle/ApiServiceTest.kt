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

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_CASE_NOT_FOUND
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TOO_MANY_TESTER_EMAILS
import com.google.firebase.appdistribution.gradle.models.AabState
import com.google.firebase.appdistribution.gradle.models.DeviceExecution
import com.google.firebase.appdistribution.gradle.models.LoginCredential
import com.google.firebase.appdistribution.gradle.models.ReleaseTest
import com.google.firebase.appdistribution.gradle.models.RoboStats
import com.google.firebase.appdistribution.gradle.models.TestDevice
import java.io.IOException
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ApiServiceTest {
  private val httpTransport = SuccessWithContent(JSON_BLOB)
  private val httpClient = AuthenticatedHttpClient(httpTransport)
  private val apiService = ApiService(httpClient)

  @Test
  fun testGetUploadStatus() {
    val content = FixtureUtils.getFixtureAsString("upload_status.json")
    val httpTransport = SuccessWithContent(content)
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)
    val response = apiService.getUploadStatus(APP_NAME, BinaryType.APK)
    assertEquals(RELEASE_NAME, response.response?.release?.name)
  }

  @Test
  fun testGetUploadStatus_error() {
    val httpTransport = ErrorNoContent()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)

    assertFailsWith(AppDistributionException::class) {
      apiService.getUploadStatus(APP_NAME, BinaryType.APK)
    }
  }

  @Test
  fun testCreateReleaseNotes() {
    val success = apiService.createReleaseNotes(RELEASE_NAME, "cool release notes")
    assertTrue(success)
  }

  @Test
  fun testDistributeRelease() {
    val testers = listOf("test1@google.com", "test2@google.com")
    val success = apiService.distributeRelease(RELEASE_NAME, testers, emptyList())
    assertTrue(success)
  }

  @Test
  fun testBatchAddTesters_succeeds() {
    val testers = listOf("test1@google.com", "test2@google.com")
    val success = apiService.batchAddTesters(/* project number */ 123L, testers)
    assertTrue(success)
  }

  @Test
  fun testBatchAddTesters_throwsIfTooManyTesters() {
    // Add 1001 testers to the request
    val testers = mutableListOf<String>()
    for (i in 0..1000) {
      testers.add(String.format("test%s@google.com", i))
    }
    val e =
      assertFailsWith(AppDistributionException::class) {
        apiService.batchAddTesters(/* project number */ 123L, testers)
      }
    assertEquals(TOO_MANY_TESTER_EMAILS, e.reason)
    assertTrue(e.message!!.contains("Cannot add 1001 testers, 1000 is the maximum allowed"))
  }

  @Test
  fun testBatchRemoveTesters_succeeds() {
    val testers = listOf("test1@google.com", "test2@google.com")
    val success = apiService.batchRemoveTesters(/* project number */ 123L, testers)
    assertTrue(success)
  }

  @Test
  fun testBatchRemoveTesters_throwsIfTooManyTesters() {
    // Add 1001 testers to the request
    val testers = mutableListOf<String>()
    for (i in 0..1000) {
      testers.add(String.format("test%s@google.com", i))
    }
    val e =
      assertFailsWith(AppDistributionException::class) {
        apiService.batchRemoveTesters(/* project number */ 123L, testers)
      }
    assertEquals(TOO_MANY_TESTER_EMAILS, e.reason)
    assertTrue(e.message!!.contains("Cannot remove 1001 testers, 1000 is the maximum allowed"))
  }

  @Test
  fun testBuildCreateReleaseNotesJson() {
    val releaseName = "release-name"
    val releaseNotes = "release notes"
    val jsonBody = apiService.buildCreateReleaseNotesJson(releaseName, releaseNotes)
    val jsonBodyString = jsonBody.toString()
    assertEquals(
      "{\"name\":\"release-name\",\"releaseNotes\":{\"text\":\"release notes\"}}",
      jsonBodyString
    )
  }

  @Test
  fun testBuildEnableAccessJson() {
    val releaseName = "release-name"
    val testers: List<String> = listOf("test1@google.com", "test2@google.com")
    val groupIds: List<String> = Lists.newArrayList()
    val jsonBody = apiService.buildDistributeReleaseJson(releaseName, testers, groupIds)
    assertEquals(
      "{\"name\":\"release-name\",\"testerEmails\":[\"test1@google.com\",\"test2@google.com\"],\"groupAliases\":[]}",
      jsonBody.toString()
    )
  }

  @Test
  fun testBuildTesterEmailsJson() {
    val testers: List<String> =
      ImmutableList.of("tester1@e.mail", "tester2@e.mail", "tester3@e.mail")
    val jsonBody = apiService.buildTesterEmailsJson(testers)
    assertEquals(
      "{\"emails\":[\"tester1@e.mail\",\"tester2@e.mail\",\"tester3@e.mail\"]}",
      jsonBody.toString()
    )
  }

  @Test(expected = IOException::class)
  fun aabInfo_whenRequestFails_throwsException() {
    val httpTransport = AppDistroMockHttpTransport.newBuilder().setCode(500).build()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)
    apiService.getAabInfo(APP_NAME)
  }

  @Test
  fun aabInfo_whenRequestSucceeds_returnsAppWithoutAabInfo() {
    val content = "{}"
    val httpTransport =
      AppDistroMockHttpTransport.newBuilder().setCode(200).setContent(content).build()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)
    val aabInfo = apiService.getAabInfo(APP_NAME)
    assertNull(aabInfo.aabState)
    assertNull(aabInfo.aabCertificate)
  }

  @Test
  fun aabInfo_whenRequestSucceeds_returnsAppWithAabCertificate() {
    val content = FixtureUtils.getFixtureAsString("aab_info_with_aab_certificate.json")
    val httpTransport =
      AppDistroMockHttpTransport.newBuilder().setCode(200).setContent(content).build()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)
    val aabInfo = apiService.getAabInfo(APP_NAME)
    assertEquals(aabInfo.aabCertificate?.certificateHashMd5, "test-cert-md5-hash")
    assertEquals(aabInfo.aabCertificate?.certificateHashSha1, "test-cert-sha1-hash")
    assertEquals(aabInfo.aabCertificate?.certificateHashSha256, "test-cert-sha256-hash")
    assertNull(aabInfo.aabState)
  }

  @Test
  fun aabInfo_whenRequestSucceeds_returnsAppWithAABState() {
    val content = FixtureUtils.getFixtureAsString("aab_info_with_aab_state.json")
    val httpTransport =
      AppDistroMockHttpTransport.newBuilder().setCode(200).setContent(content).build()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)
    val aabInfo = apiService.getAabInfo(APP_NAME)
    assertEquals(aabInfo.aabState, AabState.INTEGRATED)
  }

  @Test
  fun testRelease_withTestCase_whenTestCaseIsntFound_fails() {
    val httpTransport = AppDistroMockHttpTransport.newBuilder().setCode(404).build()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)
    assertFailsWith(
      AppDistributionException::class,
      AppDistributionException.formatMessage(TEST_CASE_NOT_FOUND, "invalid-test-case-id"),
    ) {
      apiService.testRelease(
        RELEASE_NAME,
        listOf(TestDevice(model = "pixel")),
        LoginCredential(google = true),
        "invalid-test-case-id",
      )
    }
  }

  @Test
  fun testRelease_withTestCase_succeeds() {
    val content = FixtureUtils.getFixtureAsString("release_test.json")
    val httpTransport =
      AppDistroMockHttpTransport.newBuilder().setCode(200).setContent(content).build()
    val httpClient = AuthenticatedHttpClient(httpTransport)
    val apiService = ApiService(httpClient)

    val response =
      apiService.testRelease(
        RELEASE_NAME,
        listOf(TestDevice(model = "pixel")),
        LoginCredential(google = true),
        "test-case-id",
      )
    assertEquals(
      ReleaseTest(
        name = "$RELEASE_NAME/testCases/test-case-id",
        deviceExecutions =
          listOf(
            DeviceExecution(
              crawlGraphUri = "http://example.com/crawlGraphUri",
              device = TestDevice(model = "pixel", locale = "US"),
              resultsStoragePath = "http://example.com/resultsStoragePath",
              roboStats = RoboStats(actionsPerformed = 25, distinctVisitedScreens = 8),
              state = "PASSED"
            ),
          ),
        loginCredential = LoginCredential(google = true),
      ),
      response,
    )
  }

  companion object {
    private const val JSON_BLOB = "{\"name\":\"foo\"}"
    private const val APP_NAME = "projects/123456789123/apps/1:123456789123:android:abc1234"
    private const val RELEASE_NAME = "$APP_NAME/releases/release-id"
  }
}
