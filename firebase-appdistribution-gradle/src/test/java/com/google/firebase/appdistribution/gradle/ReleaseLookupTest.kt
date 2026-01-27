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

import com.google.firebase.appdistribution.gradle.models.uploadstatus.Release
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatus
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatusError
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatusResponse
import com.google.firebase.appdistribution.gradle.models.uploadstatus.WrappedResponse
import java.io.IOException
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class ReleaseLookupTest {
  val apiService: ApiService = mock()
  val threadSleeper: ThreadSleeper = mock()

  val releaseLookup = ReleaseLookup(apiService, threadSleeper, 3)

  @Test
  @Throws(InterruptedException::class)
  fun testSuccessWithNoRetries() {
    whenever(apiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))

    val response = releaseLookup.pollForRelease(OPERATION_NAME, BinaryType.APK)

    assertEquals(response.response?.release, MOCK_RELEASE)
    verify(apiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(threadSleeper, times(0)).sleep(any())
  }

  @Test
  @Throws(InterruptedException::class)
  fun testSuccessWithRetries() {
    whenever(apiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(false, null, null))
      .thenReturn(UploadStatusResponse(false, null, null))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))
    val response = releaseLookup.pollForRelease(OPERATION_NAME, BinaryType.APK)
    assertEquals(response.response?.release, MOCK_RELEASE)
    verify(apiService, times(3)).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
    verify(threadSleeper, times(2)).sleep(ArgumentMatchers.any())
    verify(apiService, times(3)).getUploadStatus(OPERATION_NAME, BinaryType.APK)
  }

  fun testFailExceedsRetries() {
    whenever(apiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(false, null, null))
      .thenReturn(UploadStatusResponse(false, null, null))
      .thenReturn(UploadStatusResponse(false, null, null))
      .thenReturn(UploadStatusResponse(true, MOCK_WRAPPED_RESPONSE, null))

    assertFailsWith(AppDistributionException::class) {
      releaseLookup.pollForRelease(OPERATION_NAME, BinaryType.APK)
    }
    verify(apiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
  }

  @Test
  fun testFailUploadFailure() {
    whenever(apiService.getUploadStatus(any(), any()))
      .thenReturn(UploadStatusResponse(true, null, MOCK_ERROR))

    val response = releaseLookup.pollForRelease(OPERATION_NAME, BinaryType.APK)

    assertEquals(response.error?.message, MOCK_ERROR.message)
    verify(apiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
  }

  @Test(expected = RuntimeException::class)
  fun testFailBackendError() {
    whenever(apiService.getUploadStatus(any(), any())).thenThrow(IOException())

    assertFailsWith(RuntimeException::class) {
      releaseLookup.pollForRelease(OPERATION_NAME, BinaryType.APK)
    }
    verify(apiService).getUploadStatus(eq(OPERATION_NAME), eq(BinaryType.APK))
  }

  companion object {
    private const val APP_NAME = "projects/123/apps/1:123:android:abc"
    private val RELEASE_NAME = "${APP_NAME}/releases/release-id"
    private val OPERATION_NAME = "${RELEASE_NAME}/operations/release-hash"
    private val MOCK_RELEASE =
      Release(RELEASE_NAME, "1.1", "123", "firebaseConsoleUri", "testingUri", "binaryDownloadUri")
    private val MOCK_WRAPPED_RESPONSE = WrappedResponse(UploadStatus.RELEASE_CREATED, MOCK_RELEASE)
    private val MOCK_ERROR = UploadStatusError("error")
  }
}
