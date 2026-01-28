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

import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.AAB_APP_ERROR_APP_NOT_PUBLISHED
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.AAB_APP_ERROR_PLAY_ACCOUNT_NOT_LINKED
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.AAB_APP_ERROR_TOS_NOT_ACCEPTED
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.AAB_PROCESSING_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.APP_NOT_ONBOARDED_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.GET_APP_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.STARTING_TEST_FAILED
import com.google.firebase.appdistribution.gradle.models.AabCertificate
import com.google.firebase.appdistribution.gradle.models.AabInfo
import com.google.firebase.appdistribution.gradle.models.AabState
import com.google.firebase.appdistribution.gradle.models.ReleaseTest
import com.google.firebase.appdistribution.gradle.models.TestDevice
import com.google.firebase.appdistribution.gradle.models.WrappedErrorResponse
import com.google.firebase.appdistribution.gradle.models.uploadstatus.Release
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatus
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatusResponse
import com.google.firebase.appdistribution.gradle.models.uploadstatus.WrappedResponse
import com.google.gson.Gson
import java.io.IOException
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class FirebaseAppDistributionUploadTest {
  private val mockApiService: ApiService = mock()
  private val mockUploadService: UploadService = mock()
  private val mockHttpResponseException: HttpResponseException = mock()
  private val testLookup: TestLookup = mock()

  private lateinit var apkPath: String
  private lateinit var aabPath: String

  @Before
  fun setUp() {
    apkPath = FixtureUtils.getFixtureAsFile("test.apk").absolutePath
    aabPath = FixtureUtils.getFixtureAsFile("test.aab").absolutePath
  }

  @Test
  fun testUploadDistribution_acceptsNullReleaseNotesTestersAndGroups() {
    val options =
      UploadDistributionOptions(
        binaryPath = apkPath,
        appId = APP_ID,
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(mockApiService, times(0)).createReleaseNotes(any(), any())
    verify(mockApiService, times(0)).distributeRelease(any(), any(), any())
  }

  @Test
  fun testUploadDistribution_existingRelease() {
    val options =
      UploadDistributionOptions(
        binaryPath = apkPath,
        appId = APP_ID,
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(mockUploadService, times(1)).uploadDistribution(any(), any())
    verify(mockApiService, times(1)).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
  }

  @Test
  fun uploadDistribution_whenExistingReleaseCheckHas404_completesUpload() {
    val options =
      UploadDistributionOptions(
        binaryPath = apkPath,
        appId = APP_ID,
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(false, null, null))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService, times(2)).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(mockUploadService, times(1)).uploadDistribution(any(), any())
  }

  @Test
  fun uploadDistribution_uploadApkRequestFailsWith404_throwsAppNotOnboardedError() {
    val options =
      UploadDistributionOptions(
        binaryPath = apkPath,
        appId = APP_ID,
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).doAnswer {
      throw HttpResponseException.Builder(404, "NOT_FOUND", HttpHeaders()).build()
    }

    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(AppDistributionException::class, APP_NOT_ONBOARDED_ERROR.message) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenGetAabInfoRequestFailsWith403_throwsAuthExceptionWithResponseContent() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME)).then {
      throw HttpResponseException.Builder(403, "forbidden", HttpHeaders())
        .setContent("fake-response")
        .build()
    }
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(AppDistributionException::class, "${GET_APP_ERROR.message}: [403] forbidden") {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenGetAabInfoRequestFailsWith403_throwsAuthExceptionWithParsedErrorMessage() {
    val forbiddenResponse = FixtureUtils.getFixtureAsString("forbidden_response.json")
    val expectedSubstring = " Firebase App Distribution API" // see JSON fixture
    Gson()
      .fromJson(
        forbiddenResponse,
        WrappedErrorResponse::class.java
      ) // make sure the fixture is valid first
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME)).then {
      throw HttpResponseException.Builder(403, "forbidden", HttpHeaders())
        .setContent(forbiddenResponse)
        .build()
    }
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(
      AppDistributionException::class,
      "${GET_APP_ERROR.message}: [403]$expectedSubstring"
    ) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenGetAabInfoRequestFails_gracefullyHandlesUnparseableErrorMessage() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME)).then {
      throw HttpResponseException.Builder(403, "forbidden", HttpHeaders())
        .setContent("{\"invalid\":\"json")
        .build()
    }
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(AppDistributionException::class, GET_APP_ERROR.message) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenGetAabInfoRequestFailsWith404_throwsAppNotOnboardedError() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME)).then {
      throw HttpResponseException.Builder(404, "NOT_FOUND", HttpHeaders()).build()
    }
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(AppDistributionException::class, APP_NOT_ONBOARDED_ERROR.message) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenGetAabInfoRequestFailsWithout403_throwsNoAppExistsException() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME)).then { throw mockHttpResponseException }
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(AppDistributionException::class, GET_APP_ERROR.message) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenAppIsNotPublished_throwsException() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME))
      .thenReturn(AabInfo(aabState = AabState.APP_NOT_PUBLISHED))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(
      AppDistributionException::class,
      AppDistributionException.formatMessage(
        AAB_PROCESSING_ERROR,
        AAB_APP_ERROR_APP_NOT_PUBLISHED.message
      )
    ) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenAppWithBundleIdDoesntExist_throwsException() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME))
      .thenReturn(AabInfo(aabState = AabState.NO_APP_WITH_GIVEN_BUNDLE_ID_IN_PLAY_ACCOUNT))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(
      AppDistributionException::class,
      AppDistributionException.formatMessage(
        AAB_PROCESSING_ERROR,
        AppDistributionException.Reason.AAB_APP_ERROR_NO_APP_WITH_GIVEN_BUNDLE_ID_IN_PLAY_ACCOUNT
          .message
      )
    ) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenProjectIsNotLinked_throwsException() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME))
      .thenReturn(AabInfo(aabState = AabState.PLAY_ACCOUNT_NOT_LINKED))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(
      AppDistributionException::class,
      AppDistributionException.formatMessage(
        AAB_PROCESSING_ERROR,
        AAB_APP_ERROR_PLAY_ACCOUNT_NOT_LINKED.message
      )
    ) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenTosNotAccepted_throwsException() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME))
      .thenReturn(AabInfo(aabState = AabState.PLAY_IAS_TERMS_NOT_ACCEPTED))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(
      AppDistributionException::class,
      AppDistributionException.formatMessage(
        AAB_PROCESSING_ERROR,
        AAB_APP_ERROR_TOS_NOT_ACCEPTED.message
      )
    ) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenStateIsNull_throwsException() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(APP_NAME)).thenReturn(AabInfo())
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    assertFailsWith(AppDistributionException::class, AAB_PROCESSING_ERROR.message) {
      upload.uploadDistribution()
    }
  }

  @Test
  fun uploadDistribution_uploadAabWhenLinkIsIntegrated_completesUpload() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(any()))
      .thenReturn(
        AabInfo(
          aabCertificate = AabCertificate("md5", "sha1", "sha2"),
          aabState = AabState.INTEGRATED
        )
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockApiService).getAabInfo(eq(APP_NAME))
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.AAB))
  }

  @Test
  fun uploadDistribution_uploadAabWhenAabStateIsUnavailable_completesUpload() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(any()))
      .thenReturn(
        AabInfo(
          aabState = AabState.AAB_STATE_UNAVAILABLE,
          aabCertificate = AabCertificate("md5", "sha1", "sha2")
        )
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockApiService).getAabInfo(eq(APP_NAME))
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.AAB))
  }

  @Test
  fun uploadDistribution_withAab_whenAabCertificateIsInitiallyNull_completesUpload() {
    val options =
      UploadDistributionOptions(
        binaryPath = aabPath,
        appId = APP_ID,
      )
    whenever(mockApiService.getAabInfo(any()))
      .thenReturn(AabInfo(aabState = AabState.INTEGRATED))
      .thenReturn(
        AabInfo(
          aabState = AabState.INTEGRATED,
          aabCertificate = AabCertificate("md5", "sha1", "sha2")
        )
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val upload = FirebaseAppDistributionUpload(options, mockApiService, mockUploadService)

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockApiService, times(2)).getAabInfo(eq(APP_NAME))
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.AAB))
  }

  @Test
  fun uploadDistribution_withAutomatedTesting_succeeds() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = apkPath,
        testDevicesValue = TEST_DEVICES_VALUE
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    whenever(mockApiService.testRelease(any(), any(), anyOrNull(), anyOrNull()))
      .thenReturn(RELEASE_TEST)
    doNothing().whenever(testLookup).pollForReleaseTests(any(), any())
    val upload =
      FirebaseAppDistributionUpload(
        options,
        mockApiService,
        mockUploadService,
        testLookup = testLookup
      )

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(mockApiService).testRelease(eq(RELEASE_NAME), eq(TEST_DEVICES), eq(null), eq(null))
    verify(testLookup).pollForReleaseTests(eq(mockApiService), eq(setOf(RELEASE_TEST_NAME)))
  }

  @Test
  fun uploadDistribution_withAutomatedTesting_fails() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = apkPath,
        testDevicesValue = TEST_DEVICES_VALUE
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    whenever(mockApiService.testRelease(any(), any(), anyOrNull(), anyOrNull())).doAnswer {
      throw IOException()
    }
    val upload =
      FirebaseAppDistributionUpload(
        options,
        mockApiService,
        mockUploadService,
        testLookup = testLookup
      )

    val e = assertFailsWith(AppDistributionException::class) { upload.uploadDistribution() }

    val message = e.message
    assertNotNull(message)
    assertContains(message, STARTING_TEST_FAILED.message)
  }

  @Test
  fun uploadDistribution_withAutomatedTesting_withTestCases_succeeds() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = apkPath,
        testDevicesValue = TEST_DEVICES_VALUE,
        testCasesValue = "test-case-1,test-case-2,test-case-3"
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    whenever(
        mockApiService.testRelease(
          any(),
          any(),
          anyOrNull(),
          eq("${APP_NAME}/testCases/test-case-1")
        )
      )
      .thenReturn(RELEASE_TEST)
    whenever(
        mockApiService.testRelease(
          any(),
          any(),
          anyOrNull(),
          eq("${APP_NAME}/testCases/test-case-2")
        )
      )
      .thenReturn(null)
    whenever(
        mockApiService.testRelease(
          any(),
          any(),
          anyOrNull(),
          eq("${APP_NAME}/testCases/test-case-3")
        )
      )
      .thenReturn(RELEASE_TEST_2)
    doNothing().whenever(testLookup).pollForReleaseTests(any(), any())
    val upload =
      FirebaseAppDistributionUpload(
        options,
        mockApiService,
        mockUploadService,
        testLookup = testLookup
      )

    val uploadedSuccessfully = upload.uploadDistribution()

    assertTrue(uploadedSuccessfully)
    verify(mockUploadService).uploadDistribution(eq(APP_NAME), any())
    verify(mockApiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(mockApiService)
      .testRelease(
        eq(RELEASE_NAME),
        eq(TEST_DEVICES),
        eq(null),
        eq("${APP_NAME}/testCases/test-case-1")
      )
    verify(mockApiService)
      .testRelease(
        eq(RELEASE_NAME),
        eq(TEST_DEVICES),
        eq(null),
        eq("${APP_NAME}/testCases/test-case-2")
      )
    verify(mockApiService)
      .testRelease(
        eq(RELEASE_NAME),
        eq(TEST_DEVICES),
        eq(null),
        eq("${APP_NAME}/testCases/test-case-3")
      )
    verify(testLookup)
      .pollForReleaseTests(eq(mockApiService), eq(setOf(RELEASE_TEST_NAME, RELEASE_TEST_2_NAME)))
  }

  @Test
  fun uploadDistribution_withAutomatedTesting_withTestCases_fails() {
    val options =
      UploadDistributionOptions(
        appId = APP_ID,
        binaryPath = apkPath,
        testDevicesValue = TEST_DEVICES_VALUE,
        testCasesValue = "test-case-1,test-case-2"
      )
    whenever(mockUploadService.uploadDistribution(any(), any())).thenReturn(OPERATION_NAME)
    whenever(mockApiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    whenever(
        mockApiService.testRelease(
          any(),
          any(),
          anyOrNull(),
          eq("${APP_NAME}/testCases/test-case-1")
        )
      )
      .thenReturn(RELEASE_TEST)
    whenever(
        mockApiService.testRelease(
          any(),
          any(),
          anyOrNull(),
          eq("${APP_NAME}/testCases/test-case-2")
        )
      )
      .doAnswer { throw IOException() }
    val upload =
      FirebaseAppDistributionUpload(
        options,
        mockApiService,
        mockUploadService,
        testLookup = testLookup
      )

    val e = assertFailsWith(AppDistributionException::class) { upload.uploadDistribution() }

    val message = e.message
    assertNotNull(message)
    assertContains(message, STARTING_TEST_FAILED.message)
  }

  companion object {
    private const val APP_ID = "1:123:android:abc"
    private val APP_NAME = String.format("projects/123/apps/%s", APP_ID)
    private val RELEASE_NAME = String.format("%s/releases/release-id", APP_NAME)
    private val OPERATION_NAME = String.format("%s/operations/release-hash", RELEASE_NAME)
    private val MOCK_RELEASE =
      Release(RELEASE_NAME, "1.1", "123", "firebaseConsoleUri", "testingUri", "binaryDownloadUri")
    private val MOCK_WRAPPED_RESPONSE = WrappedResponse(UploadStatus.RELEASE_CREATED, MOCK_RELEASE)
    private const val TEST_DEVICES_VALUE =
      "model=pixel,version=1,orientation=portrait,locale=en_US;model=galaxy,version=2,orientation=landscape,locale=es_ES"
    private val TEST_DEVICES =
      listOf(
        TestDevice(model = "pixel", version = "1", orientation = "portrait", locale = "en_US"),
        TestDevice(model = "galaxy", version = "2", orientation = "landscape", locale = "es_ES")
      )
    private val RELEASE_TEST_NAME = "$RELEASE_NAME/releaseTests/release-test-id-1"
    private val RELEASE_TEST_2_NAME = "$RELEASE_NAME/releaseTests/release-test-id-2"
    private val RELEASE_TEST = ReleaseTest(name = RELEASE_TEST_NAME)
    private val RELEASE_TEST_2 = ReleaseTest(name = RELEASE_TEST_2_NAME)
  }
}
