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

import com.google.common.collect.Lists
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_CASE_WITH_LOGIN_RESOURCES
import java.io.IOException
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class UploadDistributionOptionsTest {
  @Test
  fun testSetReleaseNotes_withValue_usesReleaseNotesFromValue() {
    val expected = "some release notes"
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        releaseNotesValue = expected,
      )
    assertEquals(expected, options.releaseNotes)
  }

  @Test
  fun testSetReleaseNotes_withPath_loadsReleaseNotesFromPath() {
    val releaseNotesPath = FixtureUtils.getFixtureAsFile("release_notes_2.txt").absolutePath
    val expected = FixtureUtils.getFixtureAsString("release_notes_2.txt")
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        releaseNotesPath = releaseNotesPath,
      )
    assertEquals(expected, options.releaseNotes)
  }

  @Test
  fun testSetReleaseNotes_withValueAndPath_usesReleaseNotesFromValue() {
    val releaseNotesPath = FixtureUtils.getFixtureAsFile("release_notes_2.txt").absolutePath
    val expected = "Passed in release notes"
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        releaseNotesValue = expected,
        releaseNotesPath = releaseNotesPath,
      )
    assertEquals(expected, options.releaseNotes)
  }

  @Test
  fun testSetTesters_withValue_usesTestersFromValue() {
    val testers = "foo@foo.com,bar@bar.com"
    val expected: List<String> = Lists.newArrayList("foo@foo.com", "bar@bar.com")
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testersValue = testers,
      )
    assertEquals(expected, options.testers)
  }

  @Test
  fun testSetTesters_withPath_loadsTestersFromPath() {
    val testersPath = FixtureUtils.getFixtureAsFile("testers.txt").absolutePath
    val expected = listOf("a@a.com", "b@b.com")
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testersPath = testersPath,
      )
    assertEquals(expected, options.testers)
  }

  @Test
  fun testSetTesters_withPathMixedSeparators_parsesValueFromFile() {
    val testersPath = FixtureUtils.getFixtureAsFile("testers_mixed_separators.csv").absolutePath
    val expected: List<String> = Lists.newArrayList("a@a.com", "b@b.com", "c@c.com", "d@d.com")
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testersPath = testersPath,
      )
    assertEquals(expected, options.testers)
  }

  @Test
  fun testSetAppId_Successfully() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
      )
    assertEquals(APP_ID, options.appId)
  }

  @Test
  fun testSetAppId_WithEmptyAppId_ThrowsException() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = "",
          binaryPath = APK_PATH,
        )
      }
    assertEquals(AppDistributionException.Reason.MISSING_APP_ID, e.reason)
  }

  @Test
  fun testSetAppId_WithInvalidAppId_ThrowsException() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = "invalid",
          binaryPath = APK_PATH,
        )
      }
    assertEquals(AppDistributionException.Reason.INVALID_APP_ID, e.reason)
  }

  @Test
  fun testSetDistributionFile_withApk_returnsCorrectBinaryType() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = "src/test/fixtures/test.apk",
      )

    assertEquals(FixtureUtils.getFixtureAsFile("test.apk"), options.binary)
    assertEquals(BinaryType.APK, options.binaryType)
  }

  @Test
  fun testSetDistributionFile_withAab_returnsCorrectBinaryType() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = "src/test/fixtures/test.aab",
      )

    assertEquals(FixtureUtils.getFixtureAsFile("test.aab"), options.binary)
    assertEquals(BinaryType.AAB, options.binaryType)
  }

  @Test
  fun testSetLoginCredentials_returnsCredentialForUsernameAndPassword() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testUsername = "username1",
        testPassword = "password1",
      )

    assertEquals(options.testLoginCredential!!.username, "username1")
    assertEquals(options.testLoginCredential!!.password, "password1")
    assertNull(options.testLoginCredential!!.fieldHints)
  }

  @Test
  @Throws(IOException::class)
  fun testSetLoginCredentials_returnsCredentialForUsernameAndPasswordFile() {
    val passwordFilePath = FixtureUtils.getFixtureAsFile("test_password_file.txt").absolutePath
    val passwordInFile = FixtureUtils.getFixtureAsString("test_password_file.txt")

    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testUsername = "username1",
        testPasswordPath = passwordFilePath,
      )

    assertEquals(options.testLoginCredential!!.username, "username1")
    assertEquals(options.testLoginCredential!!.password, passwordInFile)
    assertNull(options.testLoginCredential!!.fieldHints)
  }

  @Test
  fun testSetLoginCredentials_prioritizesPasswordOverPasswordFile() {
    val passwordFilePath = FixtureUtils.getFixtureAsFile("test_password_file.txt").absolutePath

    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testUsername = "username1",
        testPassword = "password NOT in path",
        testPasswordPath = passwordFilePath,
      )

    assertEquals(options.testLoginCredential!!.username, "username1")
    assertEquals(options.testLoginCredential!!.password, "password NOT in path")
    assertNull(options.testLoginCredential!!.fieldHints)
  }

  @Test
  fun testSetLoginCredentials_trimsPasswordFromFile() {
    val passwordFilePath =
      FixtureUtils.getFixtureAsFile("test_password_file_with_spaces.txt").absolutePath

    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testUsername = "username1",
        testPasswordPath = passwordFilePath,
      )

    assertEquals(options.testLoginCredential!!.username, "username1")
    assertEquals(options.testLoginCredential!!.password, "test_password_in_file")
    assertNull(options.testLoginCredential!!.fieldHints)
  }

  @Test
  fun testSetLoginCredentials_returnsCredentialWithResourceNames() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testUsername = "username1",
        testPassword = "password1",
        testUsernameResource = "usernameResource1",
        testPasswordResource = "passwordResource1",
      )

    assertEquals(options.testLoginCredential!!.username, "username1")
    assertEquals(options.testLoginCredential!!.password, "password1")
    assertEquals(
      options.testLoginCredential!!.fieldHints!!.usernameResourceName,
      "usernameResource1"
    )
    assertEquals(
      options.testLoginCredential!!.fieldHints!!.passwordResourceName,
      "passwordResource1"
    )
  }

  @Test
  fun testSetLoginCredentials_returnsNullWhenNoOptionsProvided() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
      )

    assertNull(options.testLoginCredential)
  }

  @Test
  fun testSetLoginCredentials_throwsWhenPasswordFileDoesNotExist() {
    assertFailsWith(IllegalArgumentException::class) {
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testUsername = "username1",
        testPasswordPath = "/path/does/not/exist",
      )
    }
  }

  @Test
  fun testSetLoginCredentials_throwsWhenUsernameProvidedWithoutPassword() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = APP_ID,
          binaryPath = APK_PATH,
          testUsername = "username1",
        )
      }

    assertEquals(AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_MISMATCH, e.reason)
  }

  @Test
  fun testSetLoginCredentials_throwsWhenPasswordProvidedWithoutUsername() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = APP_ID,
          binaryPath = APK_PATH,
          testPassword = "password1",
        )
      }

    assertEquals(AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_MISMATCH, e.reason)
  }

  @Test
  fun testSetLoginCredentials_throwsWhenResourcesProvidedWithoutUsernameOrPassword() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = APP_ID,
          binaryPath = APK_PATH,
          testUsernameResource = "usernameResource1",
          testPasswordResource = "passwordResource1",
        )
      }

    assertEquals(AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_MISSING, e.reason)
  }

  @Test
  fun testSetLoginCredentials_throwsWhenUsernameResourceProvidedWithoutPasswordResource() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = APP_ID,
          binaryPath = APK_PATH,
          testUsernameResource = "usernameResource1",
        )
      }

    assertEquals(AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_RESOURCE_MISMATCH, e.reason)
  }

  @Test
  fun testSetLoginCredentials_throwsWhenPasswordResourceProvidedWithoutUsernameResource() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = APP_ID,
          binaryPath = APK_PATH,
          testPasswordResource = "passwordResource1",
        )
      }

    assertEquals(AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_RESOURCE_MISMATCH, e.reason)
  }

  @Test
  fun testTestCases_withValue_usesTestCasesFromValue() {
    val testCases = "test-case-1,test-case-2"
    val expected = listOf("test-case-1", "test-case-2")
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testCasesValue = testCases,
      )
    assertEquals(expected, options.testCases)
  }

  @Test
  fun testSetTestCases_withPath_loadsTestCasesFromPath() {
    val testCasesPath = FixtureUtils.getFixtureAsFile("test_cases.txt").absolutePath
    val expected = listOf("file-test-case-1", "file-test-case-2", "file-test-case-3")
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testCasesPath = testCasesPath,
      )
    assertEquals(expected, options.testCases)
  }

  @Test
  fun testSetTestCases_withValueAndPath_usesTestCasesFromValue() {
    val testCasesPath = FixtureUtils.getFixtureAsFile("test_cases.txt").absolutePath
    val testCases = "test-case-1,test-case-2"
    val expected = listOf("test-case-1", "test-case-2")

    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = APK_PATH,
        testCasesValue = testCases,
        testCasesPath = testCasesPath,
      )

    assertEquals(expected, options.testCases)
  }

  @Test
  fun testTestCases_withUsernameAndPasswordResources_fails() {
    val testCases = "test-case-1,test-case-2"

    val e =
      assertFailsWith(AppDistributionException::class) {
        UploadDistributionOptions(
          appId = APP_ID,
          binaryPath = APK_PATH,
          testCasesValue = testCases,
          testUsername = "username",
          testPassword = "password",
          testUsernameResource = "username-resource",
          testPasswordResource = "password-resource"
        )
      }

    val message = e.message
    assertNotNull(message)
    assertContains(message, TEST_CASE_WITH_LOGIN_RESOURCES.message)
  }

  companion object {
    const val APP_ID = "1:123456789:android:abc123"
    const val APK_PATH = "src/test/fixtures/test.apk"
  }
}
